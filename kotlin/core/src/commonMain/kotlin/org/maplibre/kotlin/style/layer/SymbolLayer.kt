package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.expression.Value
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.renderer.bucket.SymbolBucket
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer

/**
 * Symbol layer: labels point features with text.
 * Ported from mbgl::style::SymbolLayer (simplified: point placement, plain
 * text fields; no icon atlas, no collision detection, no glyph shaping).
 */
class SymbolLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom/feature. */
    class Evaluated(
        val size: Double,
        val color: Color,
        val opacity: Double,
        /** text-anchor: center/top/bottom/left/right (x/y offset, 0..1). */
        val anchorX: Double,
        val anchorY: Double,
        val offsetX: Double,
        val offsetY: Double,
    )

    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        size = evaluateNumber("text-size", Group.Layout, zoom, feature, 16.0),
        color = evaluateColor("text-color", Group.Paint, zoom, feature, Color.black()),
        opacity = evaluateNumber("text-opacity", Group.Paint, zoom, feature, 1.0),
        anchorX = anchorOffset("text-anchor", zoom, 0),
        anchorY = anchorOffset("text-anchor", zoom, 1),
        offsetX = evaluateNumber("text-offset", Group.Layout, zoom, feature, 0.0),
        offsetY = 0.0,
    )

    private fun anchorOffset(name: String, zoom: Float, axis: Int): Double {
        val anchor = evaluateEnum(name, Group.Layout, zoom, "center", ANCHORS)
        return ANCHOR_OFFSETS[anchor]?.get(axis) ?: 0.5
    }

    /** Resolves the text of a feature (text-field constant or expression). */
    fun textOf(feature: TileFeature): String? {
        val v = evaluateValue("text-field", Group.Layout, 0f, feature, Value.Null)
        return when (v) {
            is Value.String -> v.value
            is Value.Number -> formatNumber(v.value)
            else -> null
        }
    }

    private fun formatNumber(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

    /** Builds a symbol bucket for this layer from a decoded tile layer. */
    fun buildBucket(tileLayer: TileLayer): SymbolBucket {
        val bucket = SymbolBucket()
        bucket.build(tileLayer, { feature -> matches(feature) }) { feature -> textOf(feature) }
        return bucket
    }

    private companion object {
        val ANCHORS = mapOf(
            "center" to "center", "top" to "top", "bottom" to "bottom",
            "left" to "left", "right" to "right",
            "top-left" to "top-left", "top-right" to "top-right",
            "bottom-left" to "bottom-left", "bottom-right" to "bottom-right",
        )
        /** Anchor -> (fraction of width, fraction of height) to subtract. */
        val ANCHOR_OFFSETS = mapOf(
            "center" to listOf(0.5, 0.5),
            "top" to listOf(0.5, 0.0),
            "bottom" to listOf(0.5, 1.0),
            "left" to listOf(0.0, 0.5),
            "right" to listOf(1.0, 0.5),
            "top-left" to listOf(0.0, 0.0),
            "top-right" to listOf(1.0, 0.0),
            "bottom-left" to listOf(0.0, 1.0),
            "bottom-right" to listOf(1.0, 1.0),
        )
    }
}
