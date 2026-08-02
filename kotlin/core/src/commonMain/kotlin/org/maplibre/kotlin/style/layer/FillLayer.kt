package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.renderer.bucket.FillBucket
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer

/**
 * Fill layer: paints polygon features with a solid color.
 * Ported from mbgl::style::FillLayer.
 */
class FillLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom/feature. */
    class Evaluated(
        val color: Color,
        val opacity: Double,
        val outlineColor: Color,
        val antialias: Boolean,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        color = evaluateColor("fill-color", Group.Paint, zoom, feature, Color.black()),
        opacity = evaluateNumber("fill-opacity", Group.Paint, zoom, feature, 1.0),
        outlineColor = evaluateColor("fill-outline-color", Group.Paint, zoom, feature, Color.transparent()),
        antialias = evaluateValue("fill-antialias", Group.Paint, zoom, feature, ValueBooleanTrue) == ValueBooleanTrue,
    )

    /**
     * Builds a fill bucket for this layer from a decoded tile layer.
     * Features failing the layer filter are skipped.
     */
    fun buildBucket(tileLayer: TileLayer): FillBucket {
        val bucket = FillBucket()
        bucket.build(tileLayer) { feature -> matches(feature) }
        return bucket
    }

    private companion object {
        val ValueBooleanTrue = org.maplibre.kotlin.expression.Value.Boolean(true)
    }
}
