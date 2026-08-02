package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.renderer.bucket.LineBucket
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LineCapType
import org.maplibre.kotlin.style.LineJoinType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer

/**
 * Line layer: paints line features with a stroked width, optional gap and
 * offset, caps and joins. Ported from mbgl::style::LineLayer.
 */
class LineLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated layout properties (zoom-independent, from style spec). */
    class Layout(
        val cap: LineCapType,
        val join: LineJoinType,
        val miterLimit: Double,
        val roundLimit: Double,
    )

    /** Evaluated paint properties for one zoom/feature. */
    class Evaluated(
        val color: Color,
        val opacity: Double,
        val width: Double,
        val gapWidth: Double,
        val offset: Double,
        val blur: Double,
    )

    /** Layout options from the layer's layout group. */
    fun layout(): Layout {
        val caps = mapOf(
            "butt" to "butt", "round" to "round", "square" to "square",
        )
        val joins = mapOf(
            "miter" to "miter", "bevel" to "bevel", "round" to "round",
        )
        return Layout(
            cap = when (evaluateEnum("line-cap", Group.Layout, 0f, "butt", caps)) {
                "round" -> LineCapType.Round
                "square" -> LineCapType.Square
                else -> LineCapType.Butt
            },
            join = when (evaluateEnum("line-join", Group.Layout, 0f, "miter", joins)) {
                "bevel" -> LineJoinType.Bevel
                "round" -> LineJoinType.Round
                else -> LineJoinType.Miter
            },
            miterLimit = evaluateNumber("line-miter-limit", Group.Layout, 0f, default = 2.0),
            roundLimit = evaluateNumber("line-round-limit", Group.Layout, 0f, default = 1.05),
        )
    }

    /** Evaluated paint properties at a zoom (optionally per feature). */
    fun evaluate(zoom: Float, feature: TileFeature? = null): Evaluated = Evaluated(
        color = evaluateColor("line-color", Group.Paint, zoom, feature, Color.black()),
        opacity = evaluateNumber("line-opacity", Group.Paint, zoom, feature, 1.0),
        width = evaluateNumber("line-width", Group.Paint, zoom, feature, 1.0),
        gapWidth = evaluateNumber("line-gap-width", Group.Paint, zoom, feature, 0.0),
        offset = evaluateNumber("line-offset", Group.Paint, zoom, feature, 0.0),
        blur = evaluateNumber("line-blur", Group.Paint, zoom, feature, 0.0),
    )

    /**
     * Builds a line bucket for this layer from a decoded tile layer.
     * Layout options (caps/joins) come from the layer; features failing the
     * layer filter are skipped.
     */
    fun buildBucket(tileLayer: TileLayer): LineBucket {
        val bucket = LineBucket()
        val l = layout()
        bucket.setLayoutOptions(
            LineBucket.LayoutOptions(
                join = l.join,
                cap = l.cap,
                miterLimit = l.miterLimit.toFloat(),
                roundLimit = l.roundLimit.toFloat(),
            ),
        )
        bucket.build(tileLayer) { feature -> matches(feature) }
        return bucket
    }
}
