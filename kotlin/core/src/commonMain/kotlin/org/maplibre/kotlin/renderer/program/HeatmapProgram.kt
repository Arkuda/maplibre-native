package org.maplibre.kotlin.renderer.program

import org.maplibre.kotlin.gfx.Color

/**
 * Heatmap shader port. The GPU version evaluates a color ramp from the
 * normalized density texture; here the same ramp is built on the CPU.
 *
 * The ramp goes through up to N stops: given a density value in [0,1],
 * we interpolate between the supplied stop colors.
 */
object HeatmapProgram {

    /** One stop of the density color ramp. */
    data class ColorStop(
        val density: Float,   // in [0,1]
        val color: Color,
    )

    /** Evaluated heatmap props. */
    class Props(
        val intensity: Float = 1.0f,
        val opacity: Float = 1.0f,
        val radius: Float = 30.0f,
        val ramp: List<ColorStop> = defaultRamp(),
    )

    /**
     * Maps a normalized density to a color through the ramp.
     */
    fun rampColor(density: Float, stops: List<ColorStop>): Color {
        if (stops.isEmpty()) return Color(0f, 0f, 0f, 0f)
        if (density <= stops.first().density) return stops.first().color
        if (density >= stops.last().density) return stops.last().color

        for (i in 0 until stops.size - 1) {
            val a = stops[i]
            val b = stops[i + 1]
            if (density in a.density..b.density) {
                val t = if (b.density > a.density) {
                    (density - a.density) / (b.density - a.density)
                } else 1f
                return Color(
                    a.color.r + (b.color.r - a.color.r) * t,
                    a.color.g + (b.color.g - a.color.g) * t,
                    a.color.b + (b.color.b - a.color.b) * t,
                    a.color.a + (b.color.a - a.color.a) * t,
                )
            }
        }
        return stops.last().color
    }

    /** Default blue → green → red ramp (like MapLibre demo). */
    fun defaultRamp(): List<ColorStop> = listOf(
        ColorStop(0f, Color(0f, 0f, 1f, 1f)),
        ColorStop(0.5f, Color(0f, 1f, 0f, 1f)),
        ColorStop(1f, Color(1f, 0f, 0f, 1f)),
    )
}