package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature

/**
 * Hillshade layer: shaded relief from raster DEM tiles.
 * Ported from mbgl::style::HillshadeLayer (evaluation only; rendering of
 * DEM-derived hillshading lives in the raster pipeline).
 */
class HillshadeLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom. */
    class Evaluated(
        val illuminationDirection: Double,
        val illuminationAnchor: String,
        val exaggeration: Double,
        val shadowColor: Color,
        val highlightColor: Color,
        val accentColor: Color,
        val illuminationAltitude: Double,
        val method: String,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        illuminationDirection = evaluateNumber("hillshade-illumination-direction", Group.Paint, zoom, feature, 335.0),
        illuminationAnchor = evaluateEnum(
            "hillshade-illumination-anchor", Group.Paint, zoom, "viewport",
            mapOf("map" to "map", "viewport" to "viewport"),
        ),
        exaggeration = evaluateNumber("hillshade-exaggeration", Group.Paint, zoom, feature, 0.5),
        shadowColor = evaluateColor("hillshade-shadow-color", Group.Paint, zoom, feature, Color.black()),
        highlightColor = evaluateColor("hillshade-highlight-color", Group.Paint, zoom, feature, Color.white()),
        accentColor = evaluateColor("hillshade-accent-color", Group.Paint, zoom, feature, Color.black()),
        illuminationAltitude = evaluateNumber("hillshade-illumination-altitude", Group.Paint, zoom, feature, 45.0),
        method = evaluateEnum(
            "hillshade-method", Group.Paint, zoom, "standard",
            mapOf("standard" to "standard", "simplified" to "simplified"),
        ),
    )
}
