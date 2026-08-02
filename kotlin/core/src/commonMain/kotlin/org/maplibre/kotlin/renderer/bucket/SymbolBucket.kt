package org.maplibre.kotlin.renderer.bucket

import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint

/**
 * A text/icon label anchored at a point feature.
 * Ported from mbgl::SymbolInstance (simplified: point placement only, no
 * collision, no glyph shaping — text is a plain string).
 */
class SymbolInstance(
    val x: Double,
    val y: Double,
    val text: String?,
    val iconImage: String?,
)

/**
 * Bucket of point-feature labels. Ported from mbgl::SymbolBucket
 * (point-placement subset).
 */
class SymbolBucket {

    val instances = mutableListOf<SymbolInstance>()

    val isEmpty: Boolean get() = instances.isEmpty()

    /** Builds symbol instances for all point features of a tile layer. */
    fun build(tileLayer: TileLayer, filter: (TileFeature) -> Boolean, textOf: (TileFeature) -> String?) {
        for (feature in tileLayer.features) {
            if (!filter(feature)) continue
            val text = textOf(feature)?.takeIf { it.isNotEmpty() }
            if (text == null) continue
            for (ring in feature.geometry) {
                for (point in ring) {
                    instances.add(SymbolInstance(point.x, point.y, text, null))
                }
            }
        }
    }
}
