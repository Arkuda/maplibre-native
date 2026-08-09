package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.expression.Value
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.renderer.bucket.FillExtrusionBucket
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer

/**
 * FillExtrusion layer: extruded 3D polygons.
 * Ported from mbgl::style::FillExtrusionLayer (simplified: flat height, no pattern).
 */
class FillExtrusionLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom/feature. */
    class Evaluated(
        val height: Double,
        val base: Double,
        val color: Color,
        val opacity: Double,
        val verticalGradient: Boolean,
        val translateX: Double,
        val translateY: Double,
        val translateAnchor: String,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        height = evaluateNumber("fill-extrusion-height", Group.Paint, zoom, feature, 0.0),
        base = evaluateNumber("fill-extrusion-base", Group.Paint, zoom, feature, 0.0),
        color = evaluateColor("fill-extrusion-color", Group.Paint, zoom, feature, Color.black()),
        opacity = evaluateNumber("fill-extrusion-opacity", Group.Paint, zoom, feature, 1.0),
        verticalGradient = when (evaluateValue("fill-extrusion-vertical-gradient", Group.Paint, zoom, feature)) {
            is Value.Boolean -> (evaluateValue("fill-extrusion-vertical-gradient", Group.Paint, zoom, feature) as Value.Boolean).value
            else -> true
        },
        translateX = evaluateNumber("fill-extrusion-translate", Group.Paint, zoom, feature, 0.0),
        translateY = 0.0,
        translateAnchor = evaluateEnum("fill-extrusion-translate-anchor", Group.Paint, zoom, "map", mapOf("map" to "map", "viewport" to "viewport")),
    )

    /**
     * Builds an extrusion bucket for this layer from a decoded tile layer.
     * Features failing the layer filter are skipped; base/height are
     * evaluated per feature (data-driven expressions supported).
     */
    fun buildBucket(tileLayer: TileLayer, zoom: Float): FillExtrusionBucket {
        val bucket = FillExtrusionBucket()
        bucket.build(
            tileLayer,
            layerFilter = { feature -> matches(feature) },
            elevations = FillExtrusionBucket.ElevationEvaluator { feature ->
                val ev = evaluate(zoom, feature)
                ev.base to ev.height
            },
        )
        return bucket
    }

    companion object {
        /** Default paint properties for quick testing. */
        val DEFAULT_PAINT = mapOf<String, Any?>(
            "fill-extrusion-height" to 0.0,
            "fill-extrusion-base" to 0.0,
            "fill-extrusion-color" to "#000000",
            "fill-extrusion-opacity" to 1.0,
            "fill-extrusion-vertical-gradient" to true,
            "fill-extrusion-translate" to doubleArrayOf(0.0, 0.0),
            "fill-extrusion-translate-anchor" to "map",
        )
    }
}