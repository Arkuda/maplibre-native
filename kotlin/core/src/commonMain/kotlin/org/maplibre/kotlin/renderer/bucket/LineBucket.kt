package org.maplibre.kotlin.renderer.bucket

import org.maplibre.kotlin.geometry.LineVertex
import org.maplibre.kotlin.geometry.PolylineGenerator
import org.maplibre.kotlin.geometry.PolylineGeneratorOptions
import org.maplibre.kotlin.style.LineCapType
import org.maplibre.kotlin.style.LineJoinType
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.util.EXTENT

/**
 * A line bucket: line/polygon features converted to a triangulated line
 * skeleton ready to draw.
 *
 * Ported from mbgl::renderer::buckets::LineBucket. Every line ring is scaled
 * from the tile's native extent into tile units ([EXTENT] = 8192) and passed
 * to [PolylineGenerator], which produces vertices (position + unit extrusion)
 * and triangle indices. The renderer expands the extrusion by half the line
 * width in the vertex shader.
 */
class LineBucket {

    /** Layout properties that affect geometry generation. */
    class LayoutOptions(
        val join: LineJoinType = LineJoinType.Miter,
        val cap: LineCapType = LineCapType.Butt,
        val miterLimit: Float = 2.0f,
        val roundLimit: Float = 1.0f,
    )

    /** Vertices of the line skeleton. */
    val vertices = mutableListOf<LineVertex>()

    /** Triangle indices into [vertices]. */
    val indices = mutableListOf<Int>()

    val isEmpty: Boolean get() = vertices.isEmpty()

    /** Total line distance across all features (pre-clipping). */
    var totalDistance: Double = 0.0
        private set

    private var layout = LayoutOptions()
    private var overscaling = 1

    /** Sets layout options; must be called before [build]. */
    fun setLayoutOptions(options: LayoutOptions, overscaling: Int = 1) {
        this.layout = options
        this.overscaling = overscaling
    }

    /**
     * Builds a line bucket from a tile layer's line features.
     *
     * @param layer the decoded MVT layer
     * @param layerFilter optional predicate on feature properties
     */
    fun build(layer: TileLayer, layerFilter: ((TileFeature) -> Boolean)? = null) {
        val scale = EXTENT.toDouble() / layer.extent
        for (feature in layer.features) {
            if (feature.type != FeatureType.LINESTRING && feature.type != FeatureType.POLYGON) continue
            if (layerFilter != null && !layerFilter(feature)) continue
            for (line in feature.geometry) {
                addLine(line, feature.type, scale)
            }
        }
    }

    private fun addLine(points: List<TilePoint>, type: FeatureType, scale: Double) {
        if (points.isEmpty()) return

        // scale into tile units
        val scaled = points.map { TilePoint(it.x * scale, it.y * scale) }

        val options = PolylineGeneratorOptions(
            type = type,
            joinType = layout.join,
            miterLimit = layout.miterLimit,
            beginCap = layout.cap,
            endCap = layout.cap,
            roundLimit = layout.roundLimit,
            overscaling = overscaling,
        )
        PolylineGenerator(vertices, indices).generate(scaled, options)
    }
}
