package org.maplibre.kotlin.tile

import org.maplibre.kotlin.expression.EvaluationContext
import org.maplibre.kotlin.expression.Value

/**
 * Adapts a decoded MVT [TileFeature] to the expression engine's
 * [EvaluationContext.Feature] interface so data-driven styles can read
 * properties via `["get", ...]` etc.
 */
class FeatureAdapter(private val feature: TileFeature) : EvaluationContext.Feature {
    override val id: Value?
        get() = feature.id?.let { Value.Number(it.toDouble()) }

    override val properties: Map<String, Value>
        get() = feature.properties.mapValues { (_, v) -> v.toValue() }

    private fun TileValue.toValue(): Value = when (this) {
        is TileValue.Null -> Value.Null
        is TileValue.Str -> Value.String(value)
        is TileValue.Num -> Value.Number(value)
        is TileValue.Bool -> Value.Boolean(value)
    }
}
