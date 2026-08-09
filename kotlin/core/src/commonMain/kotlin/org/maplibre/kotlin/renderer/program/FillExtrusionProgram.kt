package org.maplibre.kotlin.renderer.program

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.math.Vec4
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Fill-extrusion program: the CPU twin of
 * shaders/fill_extrusion.vertex.glsl + .fragment.glsl.
 *
 * The C++ shader packs the surface normal into `a_normal_ed` (2^13 scale,
 * with the base/height flag `t` folded into the x component) and unpacks it
 * by dividing by 16384. This port keeps the normal unpacked and typed, and
 * carries `t` as its own field, so the math is identical.
 */
object FillExtrusionProgram {

    /** Evaluated paint + per-tile uniforms for one draw call. */
    class Uniforms(
        /** 3D matrix: tile units (x,y) + elevation (z) → clip space. */
        val matrix: Matrix4,
        /** Cartesian light direction (unit-ish vector, mbgl u_lightpos). */
        val lightPos: FloatArray,
        /** Light color rgb 0..1. */
        val lightColor: FloatArray,
        /** Light intensity (style default 0.5). */
        val lightIntensity: Double,
        val color: Color,
        val verticalGradient: Boolean,
        val opacity: Double,
    )

    /** Clip-space position + shaded color for one vertex. */
    class VertexOutput(
        val clip: Vec4,
        val color: Color,
    )

    /**
     * Vertex stage: elevates the position to base/height, projects it, and
     * shades the surface color with the diffuse light model.
     *
     * @param t 0 = base vertex, 1 = height vertex
     * @param base/height feature-evaluated elevations in meters
     */
    fun vertex(
        x: Double,
        y: Double,
        t: Double,
        nx: Double,
        ny: Double,
        nz: Double,
        base: Double,
        height: Double,
        u: Uniforms,
    ): VertexOutput {
        val baseV = max(0.0, base)
        val heightV = max(0.0, height)

        // gl_Position = u_matrix * vec4(a_pos, t > 0.0 ? height : base, 1)
        val z = if (t > 0.0) heightV else baseV
        val clip = u.matrix.times(Vec4(x.toFloat(), y.toFloat(), z.toFloat(), 1.0f))

        // Relative luminance (how dark/bright is the surface color?)
        val colorvalue = u.color.r * 0.2126 + u.color.g * 0.7152 + u.color.b * 0.0722

        // Add slight ambient lighting so no extrusions are totally black
        val ambient = 0.03

        // cos(theta), theta = angle between surface normal and diffuse light ray
        var directional = (nx * u.lightPos[0] + ny * u.lightPos[1] + nz * u.lightPos[2])
            .coerceIn(0.0, 1.0)

        // Adjust directional so that the range of highlight/shading values is
        // narrower with lower light intensity and lighter surface colors
        directional = mix(
            (1.0 - u.lightIntensity),
            max((1.0 - colorvalue + u.lightIntensity), 1.0),
            directional,
        )

        // Gradient along z axis of side surfaces: with vertical-gradient
        // enabled, walls fade toward the top; otherwise no z-gradient.
        if (ny != 0.0 && u.verticalGradient) {
            val gradient = ((t + baseV) * (heightV / 150.0).pow(0.5))
                .coerceIn(mix(0.7, 0.98, 1.0 - u.lightIntensity), 1.0)
            directional *= gradient
        }

        // Final color: surface + ambient light color, diffuse directional,
        // light color; lower bounds adjusted to hue of light so shading is
        // tinted with the complementary (opposite) color to the light color.
        // v_color *= u_opacity scales all four channels (no re-clamp).
        val r = clampChannel((u.color.r + ambient) * directional * u.lightColor[0], 1.0 - u.lightColor[0]) * u.opacity
        val g = clampChannel((u.color.g + ambient) * directional * u.lightColor[1], 1.0 - u.lightColor[1]) * u.opacity
        val b = clampChannel((u.color.b + ambient) * directional * u.lightColor[2], 1.0 - u.lightColor[2]) * u.opacity
        val a = u.opacity

        return VertexOutput(clip, Color(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat()))
    }

    /** Fragment stage: passthrough of the vertex color. */
    fun fragment(color: Color): Color = color

    /**
     * Cartesian light direction from the style light position
     * [radial, azimuthal, polar] (spherical degrees), rotated by bearing
     * when anchored to the viewport. Ported from mbgl::Position::getCartesian
     * + FillExtrusionBucket::lightPosition.
     */
    fun lightPosition(radial: Double, azimuthalDeg: Double, polarDeg: Double, bearingDeg: Double): FloatArray {
        val a = (azimuthalDeg + 90.0) * PI / 180.0
        val p = polarDeg * PI / 180.0
        var x = radial * cos(a) * sin(p)
        var y = radial * sin(a) * sin(p)
        val z = radial * cos(p)

        // rotate by -bearing around z (viewport anchor)
        val b = -bearingDeg * PI / 180.0
        val cb = cos(b)
        val sb = sin(b)
        val rx = x * cb - y * sb
        val ry = x * sb + y * cb
        x = rx
        y = ry
        return floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat())
    }

    private fun mix(a: Double, b: Double, t: Double): Double = a * (1.0 - t) + b * t

    private fun clampChannel(v: Double, lower: Double): Double = v.coerceIn(lower, 1.0)
}
