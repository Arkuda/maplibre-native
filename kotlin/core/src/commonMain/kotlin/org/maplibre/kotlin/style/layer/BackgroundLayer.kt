package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.LayerSpec

/**
 * Background layer: paints the whole map canvas with a solid color.
 * Ported from mbgl::style::BackgroundLayer.
 *
 * Unlike source layers, a background layer has no tile source — it is a
 * full-canvas quad, so there is no bucket.
 */
class BackgroundLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom. */
    class Evaluated(
        val color: Color,
        val opacity: Double,
    )

    fun evaluate(zoom: Float): Evaluated = Evaluated(
        color = evaluateColor("background-color", Group.Paint, zoom, null, Color.black()),
        opacity = evaluateNumber("background-opacity", Group.Paint, zoom, null, 1.0),
    )

    companion object {
        /** Full-canvas layers are visible only when no zoom-range clips them. */
        fun isBackground(spec: LayerSpec): Boolean = spec.source == null
    }
}
