package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.expression.EvaluationContext
import org.maplibre.kotlin.expression.Value
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.FilterEvaluator
import org.maplibre.kotlin.style.FilterSpec
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.tile.FeatureAdapter
import org.maplibre.kotlin.tile.TileFeature

/**
 * Base class of all style layers. A layer combines a typed [LayerSpec]
 * (id, source, filter, layout + paint properties) with the logic to
 * evaluate its paint/layout values at a given zoom (and optionally per
 * feature) and to build geometry buckets from decoded tiles.
 *
 * Ported from mbgl::style::Layer / mbgl::style::StyleLayer (subset).
 */
abstract class StyleLayer(val spec: LayerSpec) {

    val id: String get() = spec.id
    val type: LayerType get() = spec.type
    val source: String? get() = spec.source
    val sourceLayer: String? get() = spec.sourceLayer
    val minzoom: Float? get() = spec.minzoom
    val maxzoom: Float? get() = spec.maxzoom
    val filter: FilterSpec? get() = spec.filter

    /** Visible at the given zoom? */
    fun isVisible(zoom: Float): Boolean {
        val mn = minzoom
        if (mn != null && zoom < mn) return false
        val mx = maxzoom
        if (mx != null && zoom >= mx) return false
        return true
    }

    /** Does the layer's filter accept this feature? */
    fun matches(feature: TileFeature): Boolean = FilterEvaluator.matches(filter, feature)

    // ---- property evaluation ---------------------------------------------

    /** Raw property value from layout or paint, or null when absent. */
    fun property(name: String, group: Group): PropertyValue? {
        val table = when (group) {
            Group.Layout -> spec.layout
            Group.Paint -> spec.paint
        }
        return table[name]
    }

    /**
     * Evaluates a property at the given zoom (and optionally feature) into a
     * [Value]. Missing properties return [default].
     */
    fun evaluateValue(
        name: String,
        group: Group,
        zoom: Float,
        feature: TileFeature? = null,
        default: Value = Value.Null,
    ): Value {
        val pv = property(name, group) ?: return default
        return when (pv) {
            is PropertyValue.Constant -> constantToValue(pv.value)
            is PropertyValue.Expression -> {
                val ctx = EvaluationContext(
                    zoom = zoom,
                    feature = feature?.let { FeatureAdapter(it) },
                )
                pv.expression.evaluate(ctx).resultValue ?: default
            }
        }
    }

    private fun constantToValue(v: Any?): Value = when (v) {
        null -> Value.Null
        is Boolean -> Value.Boolean(v)
        is Int -> Value.Number(v.toDouble())
        is Long -> Value.Number(v.toDouble())
        is Double -> Value.Number(v)
        is Float -> Value.Number(v.toDouble())
        is String -> Value.String(v)
        else -> Value.String(v.toString())
    }

    /** Evaluates a property as a number with a default. */
    fun evaluateNumber(
        name: String,
        group: Group,
        zoom: Float,
        feature: TileFeature? = null,
        default: Double,
    ): Double {
        val v = evaluateValue(name, group, zoom, feature)
        return when (v) {
            is Value.Number -> v.value
            is Value.String -> v.value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    /** Evaluates a property as a color with a default. */
    fun evaluateColor(
        name: String,
        group: Group,
        zoom: Float,
        feature: TileFeature? = null,
        default: Color = Color.black(),
    ): Color {
        val v = evaluateValue(name, group, zoom, feature)
        val c = when (v) {
            is Value.Color -> Color(v.r.toFloat(), v.g.toFloat(), v.b.toFloat(), v.a.toFloat())
            is Value.String -> Color.parse(v.value)
            else -> null
        }
        return c ?: default
    }

    /** Evaluates a property as an enum value by string name. */
    fun evaluateEnum(
        name: String,
        group: Group,
        zoom: Float,
        default: String,
        values: Map<String, String>,
    ): String {
        val v = evaluateValue(name, group, zoom)
        return when (v) {
            is Value.String -> values[v.value] ?: default
            else -> default
        }
    }

    enum class Group { Layout, Paint }
}
