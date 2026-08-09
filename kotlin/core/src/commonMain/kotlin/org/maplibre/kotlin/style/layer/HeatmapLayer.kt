package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.expression.Value
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature

/**
 * Heatmap layer: density heatmap from point data.
 * Ported from mbgl::style::HeatmapLayer (evaluation only; the color ramp is
 * exposed as an expression value that the renderer samples).
 */
class HeatmapLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom/feature. */
    class Evaluated(
        val radius: Double,
        val weight: Double,
        val intensity: Double,
        val opacity: Double,
        /** Raw color-ramp value (expression), for the renderer to sample. */
        val color: Value,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        radius = evaluateNumber("heatmap-radius", Group.Paint, zoom, feature, 30.0),
        weight = evaluateNumber("heatmap-weight", Group.Paint, zoom, feature, 1.0),
        intensity = evaluateNumber("heatmap-intensity", Group.Paint, zoom, feature, 1.0),
        opacity = evaluateNumber("heatmap-opacity", Group.Paint, zoom, feature, 1.0),
        color = evaluateValue("heatmap-color", Group.Paint, zoom, feature),
    )
}
