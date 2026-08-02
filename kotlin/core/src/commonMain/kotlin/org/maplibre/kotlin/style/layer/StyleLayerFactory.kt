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
        LayerType.Fill -> FillLayer(spec)
        LayerType.Line -> LineLayer(spec)
        // Other layer types (symbol, circle, ...) are not ported yet.
        else -> null
    }

    /** Creates layers for all specs, skipping unported types. */
    fun createAll(specs: List<LayerSpec>): List<StyleLayer> =
        specs.mapNotNull { create(it) }
}
