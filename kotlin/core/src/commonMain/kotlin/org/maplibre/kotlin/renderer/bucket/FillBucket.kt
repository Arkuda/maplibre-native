package org.maplibre.kotlin.renderer.bucket

import org.maplibre.kotlin.geometry.Earcut
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.util.EXTENT
import kotlin.math.roundToInt

/**
 * A fill bucket: polygon features converted to triangle meshes ready to draw.
 *
 * Ported from mbgl::renderer::buckets::FillBucket. Each polygon ring is
 * scaled from the tile's native extent (usually 4096) into tile units
 * ([EXTENT] = 8192), outer ring + holes are fed to [Earcut], and the
 * resulting triangles are appended to a flat vertex/index pair.
 */
class FillBucket {

    /** One vertex: a position in tile units (EXTENT = 8192 world). */
    class Vertex(val x: Short, val y: Short)

    /** Vertex positions, one per unique corner. */
    val vertices = mutableListOf<Vertex>()

    /** Triangle corner indices into [vertices] (3 per triangle). */
    val indices = mutableListOf<Int>()

    /** Number of triangles produced. */
    val triangleCount: Int get() = indices.size / 3

    /** Whether this bucket has any geometry at all. */
    val isEmpty: Boolean get() = vertices.isEmpty()

    /**
     * Builds a fill bucket from a tile layer's polygon features.
     *
     * @param layer the decoded MVT layer (extent comes from the layer)
     * @param layerFilter optional predicate on feature properties; only
     *   features passing it are added (used by fill layers' `filter`)
     */
    fun build(layer: TileLayer, layerFilter: ((TileFeature) -> Boolean)? = null) {
        val scale = EXTENT.toDouble() / layer.extent
        for (feature in layer.features) {
            if (feature.type != FeatureType.POLYGON) continue
            if (layerFilter != null && !layerFilter(feature)) continue
            addPolygon(feature.geometry, scale)
        }
    }

    /**
     * Appends one polygon (list of rings; first ring = outer boundary, the
     * rest are holes) to the bucket. Rings with too few points are skipped.
     */
    private fun addPolygon(rings: List<List<TilePoint>>, scale: Double) {
        if (rings.isEmpty()) return

        val outer = rings[0]
        if (outer.size < 3) return

        // Count total points: outer ring + all valid holes.
        var total = outer.size
        for (h in 1 until rings.size) {
            if (rings[h].size >= 3) total += rings[h].size
        }

        // Flatten all rings into a single coordinate array for earcut,
        // recording where each hole starts.
        val data = DoubleArray(total * 2)
        var idx = 0
        for (p in outer) {
            data[idx++] = p.x * scale
            data[idx++] = p.y * scale
        }
        val holeIndices = IntArray(rings.size - 1)
        var count = outer.size
        for (h in 1 until rings.size) {
            val hole = rings[h]
            if (hole.size < 3) continue
            holeIndices[h - 1] = count
            for (p in hole) {
                data[idx++] = p.x * scale
                data[idx++] = p.y * scale
            }
            count += hole.size
        }

        val baseIndex = vertices.size
        val tris = Earcut.triangulate(data, holeIndices, 2)
        if (tris.isEmpty()) return

        // Add vertices for every input coordinate; earcut indices are
        // stable per input vertex, so a plain 1:1 copy works.
        val vertexCount = data.size / 2
        for (v in 0 until vertexCount) {
            vertices.add(Vertex(clampToShort(data[v * 2]), clampToShort(data[v * 2 + 1])))
        }
        for (t in tris) {
            indices.add(baseIndex + t)
        }
    }

    private fun clampToShort(v: Double): Short = when {
        v.isNaN() -> 0
        v <= Short.MIN_VALUE -> Short.MIN_VALUE
        v >= Short.MAX_VALUE -> Short.MAX_VALUE
        else -> v.roundToInt().toShort()
    }
}
