package org.maplibre.kotlin.renderer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.layer.HillshadeMethod
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU-side hillshading: turns a DEM elevation grid into a shaded RGBA image.
 * Ported 1:1 from shaders/hillshade_prepare.fragment.glsl (Sobel slope
 * derivative) + shaders/hillshade.fragment.glsl (the five shading methods:
 * standard, basic, combined, igor, multidirectional).
 *
 * The DEM is a [dimension] x [dimension] grid of elevations in meters
 * (the C++ texture encodes them; decoding lives in the DEM tile source).
 */
object HillshadeRasterizer {

    /** Shading inputs, from a HillshadeLayer.Evaluated + camera context. */
    class Input(
        val exaggeration: Double = 0.5,
        val shadowColor: Color = Color.black(),
        val highlightColor: Color = Color.white(),
        val accentColor: Color = Color.black(),
        val illuminationDirectionDeg: Double = 335.0,
        val illuminationAltitudeDeg: Double = 45.0,
        val method: HillshadeMethod = HillshadeMethod.STANDARD,
    )

    /**
     * Shades a DEM grid.
     *
     * @param dem elevation in meters, [dimension] x [dimension], row-major
     * @param zoom the tile zoom (controls the prepare-pass derivative scale)
     * @param latRange (topLat, bottomLat) in degrees, for mercator
     *   distortion correction; (0, 0) disables it
     * @return premultiplied RGBA bytes, row-major, top-down
     */
    fun shade(
        dem: FloatArray,
        dimension: Int,
        zoom: Double,
        input: Input,
        latRange: Pair<Double, Double> = 0.0 to 0.0,
    ): ByteArray {
        require(dem.size == dimension * dimension) { "DEM size must be dimension^2" }
        val pixels = ByteArray(dimension * dimension * 4)
        val epsilon = 1.0 / dimension
        val tileSize = dimension - 2.0

        // prepare-pass exaggeration (depends on zoom, not the style value)
        val exaggerationFactor = when {
            zoom < 2.0 -> 0.4
            zoom < 4.5 -> 0.35
            else -> 0.3
        }
        val zoomExaggeration = if (zoom < 15.0) (zoom - 15.0) * exaggerationFactor else 0.0
        val derivScale = tileSize / 2.0.pow(zoomExaggeration + (28.2562 - zoom))

        // u_azimuths.x = deg2rad(illumination-direction); the standard/basic/
        // combined/igor methods add PI internally, multidirectional does not.
        val azimuthRad = input.illuminationDirectionDeg * PI / 180.0
        val azimuth = azimuthRad + PI
        val altitude = input.illuminationAltitudeDeg * PI / 180.0

        for (y in 0 until dimension) {
            val vPosY = y.toDouble() / (dimension - 1)
            // mercator distortion at this latitude
            val scaleFactor = cos((latRange.first - latRange.second) * (1.0 - vPosY) + latRange.second)
            for (x in 0 until dimension) {
                val cx = x.toDouble() / (dimension - 1)
                val e = epsilon

                // Sobel operator over the 3x3 neighborhood
                val a = elev(dem, dimension, cx - e, vPosY - e)
                val b = elev(dem, dimension, cx, vPosY - e)
                val c = elev(dem, dimension, cx + e, vPosY - e)
                val d = elev(dem, dimension, cx - e, vPosY)
                val f = elev(dem, dimension, cx + e, vPosY)
                val g = elev(dem, dimension, cx - e, vPosY + e)
                val h = elev(dem, dimension, cx, vPosY + e)
                val i = elev(dem, dimension, cx + e, vPosY + e)

                // world-space slope derivative (prepare pass output)
                val dx = ((c + f + f + i) - (a + d + d + g)) * derivScale
                val dy = ((g + h + h + i) - (a + b + b + c)) * derivScale

                // shade pass: the prepare output already IS the world-space
                // slope (the GLSL encode/decode through the texture cancels
                // out); only the mercator latitude correction remains
                val sdx = dx / scaleFactor
                val sdy = dy / scaleFactor

                val color = when (input.method) {
                    HillshadeMethod.STANDARD -> standard(sdx, sdy, azimuth, input)
                    HillshadeMethod.BASIC -> basic(sdx, sdy, azimuth, altitude, input)
                    HillshadeMethod.COMBINED -> combined(sdx, sdy, azimuth, altitude, input)
                    HillshadeMethod.IGOR -> igor(sdx, sdy, azimuth, input)
                    HillshadeMethod.MULTIDIRECTIONAL -> multidirectional(sdx, sdy, azimuthRad, altitude, input)
                }

                val idx = (y * dimension + x) * 4
                // premultiplied RGBA
                pixels[idx] = (color.r * 255).toInt().coerceIn(0, 255).toByte()
                pixels[idx + 1] = (color.g * 255).toInt().coerceIn(0, 255).toByte()
                pixels[idx + 2] = (color.b * 255).toInt().coerceIn(0, 255).toByte()
                pixels[idx + 3] = (color.a * 255).toInt().coerceIn(0, 255).toByte()
            }
        }
        return pixels
    }

    /** Bilinear-ish sample of the DEM at normalized coords, clamped at edges. */
    private fun elev(dem: FloatArray, dimension: Int, u: Double, v: Double): Double {
        val x = (u * (dimension - 1)).toInt().coerceIn(0, dimension - 1)
        val y = (v * (dimension - 1)).toInt().coerceIn(0, dimension - 1)
        return dem[y * dimension + x].toDouble()
    }

    // ---- shading methods (ported from hillshade.fragment.glsl) ------------

    /** Method 0: legacy MapLibre hillshade. */
    private fun standard(dx: Double, dy: Double, azimuth: Double, input: Input): Color {
        val slope = atan(0.625 * length(dx, dy))
        val aspect = getAspect(dx, dy)
        val intensity = input.exaggeration

        val base = 1.875 - intensity * 1.75
        val maxValue = 0.5 * PI
        val scaledSlope = if (abs(intensity - 0.5) > 1e-6) {
            ((base.pow(slope) - 1.0) / (base.pow(maxValue) - 1.0)) * maxValue
        } else {
            slope
        }

        val accent = cos(scaledSlope)
        val accentColor = input.accentColor * ((1.0 - accent) * (intensity * 2.0).coerceIn(0.0, 1.0))

        val shade = abs(mod((aspect + azimuth) / PI + 0.5, 2.0) - 1.0)
        val shadeColor = mix(input.shadowColor, input.highlightColor, shade) *
            sin(scaledSlope) * (intensity * 2.0).coerceIn(0.0, 1.0)

        // accent_color * (1 - shade_color.a) + shade_color
        return accentColor * (1.0 - shadeColor.a) + shadeColor
    }

    /** Method 4: basic directional hillshade. */
    private fun basic(dx: Double, dy: Double, azimuth: Double, altitude: Double, input: Input): Color {
        val ex = dx * input.exaggeration * 2.0
        val ey = dy * input.exaggeration * 2.0
        val cosAz = cos(azimuth)
        val sinAz = sin(azimuth)
        val cosAlt = cos(altitude)
        val sinAlt = sin(altitude)

        val cang = ((sinAlt - (ey * cosAz * cosAlt - ex * sinAz * cosAlt)) / sqrt(1.0 + dot(ex, ey)))
            .coerceIn(0.0, 1.0)

        return if (cang > 0.5) {
            input.highlightColor * (2.0 * cang - 1.0)
        } else {
            input.shadowColor * (1.0 - 2.0 * cang)
        }
    }

    /** Method 3: multidirectional (single light in this port). */
    private fun multidirectional(dx: Double, dy: Double, azimuth: Double, altitude: Double, input: Input): Color {
        val ex = dx * input.exaggeration * 2.0
        val ey = dy * input.exaggeration * 2.0
        val cosAz = -cos(azimuth)
        val sinAz = -sin(azimuth)
        val cosAlt = cos(altitude)
        val sinAlt = sin(altitude)

        val cang = ((sinAlt - (ey * cosAz * cosAlt - ex * sinAz * cosAlt)) / sqrt(1.0 + dot(ex, ey)))
            .coerceIn(0.0, 1.0)

        return if (cang > 0.5) {
            input.highlightColor * (2.0 * cang - 1.0)
        } else {
            input.shadowColor * (1.0 - 2.0 * cang)
        }
    }

    /** Method 1: combined shadow + highlight. */
    private fun combined(dx: Double, dy: Double, azimuth: Double, altitude: Double, input: Input): Color {
        val ex = dx * input.exaggeration * 2.0
        val ey = dy * input.exaggeration * 2.0
        val cosAz = cos(azimuth)
        val sinAz = sin(azimuth)
        val cosAlt = cos(altitude)
        val sinAlt = sin(altitude)

        var cang = acos(((sinAlt - (ey * cosAz * cosAlt - ex * sinAz * cosAlt)) / sqrt(1.0 + dot(ex, ey)))
            .coerceIn(-1.0, 1.0))
        cang = cang.coerceIn(0.0, PI / 2.0)

        val shade = cang * atan(length(ex, ey)) * 4.0 / PI / PI
        val highlight = (PI / 2.0 - cang) * atan(length(ex, ey)) * 4.0 / PI / PI
        return input.shadowColor * shade + input.highlightColor * highlight
    }

    /** Method 2: Igor's shadow/highlight. */
    private fun igor(dx: Double, dy: Double, azimuth: Double, input: Input): Color {
        val ex = dx * input.exaggeration * 2.0
        val ey = dy * input.exaggeration * 2.0
        val aspect = getAspect(ex, ey)

        val slopeStrength = atan(length(ex, ey)) * 2.0 / PI
        val aspectStrength = 1.0 - abs(mod((aspect + azimuth) / PI + 0.5, 2.0) - 1.0)

        val shadowStrength = slopeStrength * aspectStrength
        val highlightStrength = slopeStrength * (1.0 - aspectStrength)
        return input.shadowColor * shadowStrength + input.highlightColor * highlightStrength
    }

    // ---- helpers ----------------------------------------------------------

    private fun getAspect(dx: Double, dy: Double): Double =
        if (dx != 0.0) atan2(dy, -dx) else PI / 2.0 * (if (dy > 0.0) 1.0 else -1.0)

    private fun length(x: Double, y: Double): Double = sqrt(x * x + y * y)

    private fun dot(x: Double, y: Double): Double = x * x + y * y

    /** GLSL mod: result has the sign of the divisor (positive here). */
    private fun mod(x: Double, m: Double): Double {
        val r = x % m
        return if (r < 0) r + m else r
    }

    private fun mix(a: Color, b: Color, t: Double): Color = a * (1.0 - t) + b * t
}

/** Color arithmetic helpers for the shading math (all channels incl. alpha). */
private operator fun Color.times(f: Double): Color = Color(
    (r * f).toFloat(), (g * f).toFloat(), (b * f).toFloat(), (a * f).toFloat(),
)

private operator fun Color.plus(o: Color): Color = Color(
    (r + o.r).toFloat(), (g + o.g).toFloat(), (b + o.b).toFloat(), (a + o.a).toFloat(),
)

private operator fun Color.minus(o: Color): Color = Color(
    (r - o.r).toFloat(), (g - o.g).toFloat(), (b - o.b).toFloat(), (a - o.a).toFloat(),
)
