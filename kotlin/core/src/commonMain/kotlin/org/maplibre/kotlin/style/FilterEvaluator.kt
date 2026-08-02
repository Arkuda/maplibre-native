package org.maplibre.kotlin.style

import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileValue

/**
 * Evaluates a typed [FilterSpec] against a decoded tile feature.
 * Ported from mbgl::style::expression::Filter (legacy filter syntax).
 */
object FilterEvaluator {

    fun matches(filter: FilterSpec?, feature: TileFeature): Boolean {
        if (filter == null) return true
        return when (filter) {
            is FilterSpec.Equals -> property(filter.key, feature) == filter.value
            is FilterSpec.NotEquals -> property(filter.key, feature) != filter.value
            is FilterSpec.In -> filter.values.any { property(filter.key, feature) == it }
            is FilterSpec.Has -> property(filter.key, feature) != null
            is FilterSpec.AllOf -> filter.filters.all { matches(it, feature) }
            is FilterSpec.AnyOf -> filter.filters.any { matches(it, feature) }
            is FilterSpec.NoneOf -> filter.filters.none { matches(it, feature) }
        }
    }

    private fun property(key: String, feature: TileFeature): Any? {
        if (key == "\$type") {
            return when (feature.type) {
                org.maplibre.kotlin.tile.FeatureType.POINT -> "Point"
                org.maplibre.kotlin.tile.FeatureType.LINESTRING -> "LineString"
                org.maplibre.kotlin.tile.FeatureType.POLYGON -> "Polygon"
                else -> null
            }
        }
        if (key == "\$id") return feature.id
        return feature.properties[key]?.toPlain()
    }

    private fun TileValue.toPlain(): Any? = when (this) {
        is TileValue.Null -> null
        is TileValue.Str -> value
        is TileValue.Num -> value
        is TileValue.Bool -> value
    }
}
