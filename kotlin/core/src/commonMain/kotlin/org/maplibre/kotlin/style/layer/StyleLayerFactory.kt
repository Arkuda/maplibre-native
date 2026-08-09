package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType

/**
 * Turns typed [LayerSpec]s into concrete [StyleLayer] instances.
 * Ported from mbgl::style::createStyleLayer (subset).
 */
object StyleLayerFactory {

    fun create(spec: LayerSpec): StyleLayer? = when (spec.type) {
        LayerType.Background -> BackgroundLayer(spec)
        LayerType.Circle -> CircleLayer(spec)
        LayerType.ColorRelief -> ColorReliefLayer(spec)
        LayerType.Fill -> FillLayer(spec)
        LayerType.FillExtrusion -> FillExtrusionLayer(spec)
        LayerType.Heatmap -> HeatmapLayer(spec)
        LayerType.Hillshade -> HillshadeLayer(spec)
        LayerType.Line -> LineLayer(spec)
        LayerType.LocationIndicator -> LocationIndicatorLayer(spec)
        LayerType.Raster -> RasterLayer(spec)
        LayerType.Symbol -> SymbolLayer(spec)
        else -> null
    }

    /** Creates layers for all specs, skipping unported types. */
    fun createAll(specs: List<LayerSpec>): List<StyleLayer> =
        specs.mapNotNull { create(it) }
}
