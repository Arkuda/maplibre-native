package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.expression.Value
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature

/**
 * ColorRelief layer: colorized relief from raster DEM tiles.
 * Ported from mbgl::style::ColorReliefLayer (evaluation only).
 */
class ColorReliefLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom. */
    class Evaluated(
        val opacity: Double,
        /** Raw color-ramp value (expression), for the renderer to sample. */
        val color: Value,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        opacity = evaluateNumber("color-relief-opacity", Group.Paint, zoom, feature, 1.0),
        color = evaluateValue("color-relief-color", Group.Paint, zoom, feature),
    )
}
