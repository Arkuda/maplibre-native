package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature

/**
 * LocationIndicator layer: shows the device location on the map.
 * Ported from mbgl::style::LocationIndicatorLayer (evaluation only; the
 * renderer draws the location puck from these values).
 */
class LocationIndicatorLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom. */
    class Evaluated(
        val bearing: Double,
        val bearingImageSize: Double,
        val accuracyRadius: Double,
        val accuracyRadiusColor: Color,
        val accuracyRadiusBorderColor: Color,
        val imageTiltDisplacement: Double,
        val location: Triple<Double, Double, Double>,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        bearing = evaluateNumber("bearing", Group.Paint, zoom, feature, 0.0),
        bearingImageSize = evaluateNumber("bearing-image-size", Group.Paint, zoom, feature, 1.0),
        accuracyRadius = evaluateNumber("accuracy-radius", Group.Paint, zoom, feature, 0.0),
        accuracyRadiusColor = evaluateColor("accuracy-radius-color", Group.Paint, zoom, feature, Color.white()),
        accuracyRadiusBorderColor = evaluateColor("accuracy-radius-border-color", Group.Paint, zoom, feature, Color.white()),
        imageTiltDisplacement = evaluateNumber("image-tilt-displacement", Group.Paint, zoom, feature, 0.0),
        location = Triple(0.0, 0.0, 0.0),
    )
}
