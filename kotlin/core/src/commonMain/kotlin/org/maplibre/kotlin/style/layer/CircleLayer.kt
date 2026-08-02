package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.renderer.bucket.CircleBucket
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer

/**
 * Circle layer: paints point features as circles (radius, color, stroke).
 * Ported from mbgl::style::CircleLayer.
 */
class CircleLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom/feature. */
    class Evaluated(
        val radius: Double,
        val color: Color,
        val opacity: Double,
        val strokeColor: Color,
        val strokeWidth: Double,
        val strokeOpacity: Double,
        val blur: Double,
        /** circle-pitch-scale: "map" scales with the map, "viewport" is screen-space. */
        val scaleWithMap: Boolean,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        radius = evaluateNumber("circle-radius", Group.Paint, zoom, feature, 5.0),
        color = evaluateColor("circle-color", Group.Paint, zoom, feature, Color.black()),
        opacity = evaluateNumber("circle-opacity", Group.Paint, zoom, feature, 1.0),
        strokeColor = evaluateColor("circle-stroke-color", Group.Paint, zoom, feature, Color.black()),
        strokeWidth = evaluateNumber("circle-stroke-width", Group.Paint, zoom, feature, 0.0),
        strokeOpacity = evaluateNumber("circle-stroke-opacity", Group.Paint, zoom, feature, 1.0),
        blur = evaluateNumber("circle-blur", Group.Paint, zoom, feature, 0.0),
        scaleWithMap = evaluateEnum(
            "circle-pitch-scale", Group.Paint, zoom, "map",
            mapOf("map" to "map", "viewport" to "viewport"),
        ) == "map",
    )

    /** Builds a circle bucket for this layer from a decoded tile layer. */
    fun buildBucket(tileLayer: TileLayer): CircleBucket {
        val bucket = CircleBucket()
        bucket.build(tileLayer) { feature -> matches(feature) }
        return bucket
    }
}
