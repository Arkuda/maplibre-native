package org.maplibre.kotlin.renderer.bucket

import org.maplibre.kotlin.geometry.Earcut
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.util.EXTENT
import kotlin.math.roundToInt

/**
 * A fill-extrusion bucket: polygon features converted to an extruded
 * triangle mesh (side walls + roof) ready for 3D drawing.
 *
 * Ported from mbgl::renderer::buckets::FillExtrusionBucket. Each polygon
 * ring produces:
 *  - one roof vertex per point: normal = (0, 0, 1), t = 1 (at `height`)
 *  - four wall vertices per edge: normal = unit(perp(edge)), t = 0 at
 *    `base` / t = 1 at `height`; two triangles per edge
 *  - a roof fan from earcut (winding swapped like the C++ code)
 *
 * The C++ side packs the normal into a_normal_ed (2^13 scale, `t` folded
 * into the x component); here the values are kept unpacked and typed.
 */
class FillExtrusionBucket {

    /** One vertex: position in tile units + unpacked normal + base/height flag. */
    class Vertex(
        val x: Double,
        val y: Double,
        /** Surface normal (unit), in the same scale the shader uses (already / 16384). */
        val nx: Double,
        val ny: Double,
        val nz: Double,
        /** 0 = at base, 1 = at height. */
        val t: Int,
        /** Edge distance along the ring (for patterns; kept for parity). */
        val edgeDist: Int,
        /** Feature-evaluated base elevation (meters). */
        val base: Double,
        /** Feature-evaluated height (meters). */
        val height: Double,
    )

    /** Per-feature elevation lookup, like the C++ paint binders. */
    fun interface ElevationEvaluator {
        fun evaluate(feature: TileFeature): Pair<Double, Double> // (base, height)
    }

    /** Vertex positions, one per unique corner. */
    val vertices = mutableListOf<Vertex>()

    /** Triangle corner indices into [vertices] (3 per triangle). */
    val indices = mutableListOf<Int>()

    /** Number of triangles produced. */
    val triangleCount: Int get() = indices.size / 3

    /** Whether this bucket has any geometry at all. */
    val isEmpty: Boolean get() = vertices.isEmpty()

    /**
     * Builds an extrusion bucket from a tile layer's polygon features.
     *
     * @param layer the decoded MVT layer (extent comes from the layer)
     * @param layerFilter optional predicate on feature properties; only
     *   features passing it are added (used by fill-extrusion layers' `filter`)
     * @param elevations per-feature (base, height); absent features get (0, 0)
     */
    fun build(
        layer: TileLayer,
        layerFilter: ((TileFeature) -> Boolean)? = null,
        elevations: ElevationEvaluator? = null,
    ) {
        val scale = EXTENT.toDouble() / layer.extent
        for (feature in layer.features) {
            if (feature.type != FeatureType.POLYGON) continue
            if (layerFilter != null && !layerFilter(feature)) continue
            val (base, height) = elevations?.evaluate(feature) ?: (0.0 to 0.0)
            addPolygon(feature.geometry, scale, base, height)
        }
    }

    /** Appends one extruded polygon (rings[0] = outer boundary, rest holes). */
    private fun addPolygon(rings: List<List<TilePoint>>, scale: Double, base: Double, height: Double) {
        if (rings.isEmpty()) return
        if (rings[0].size < 3) return

        // Keep only usable rings (outer + holes with >= 3 points), preserving order.
        val valid = rings.filter { it.size >= 3 }
        if (valid.isEmpty()) return
        var total = 0
        for (ring in valid) total += ring.size
        if (total == 0) return

        // Roof vertices first: one per input point, normal pointing up (0,0,1),
        // t = 1 (the roof sits at `height`).
        val flatIndices = IntArray(total)
        val data = DoubleArray(total * 2)
        var idx = 0
        var flatIdx = 0
        for (ring in valid) {
            var edgeDistance = 0
            for (i in ring.indices) {
                val p = ring[i]
                flatIndices[flatIdx++] = vertices.size
                vertices.add(Vertex(p.x * scale, p.y * scale, 0.0, 0.0, 1.0, t = 1, edgeDist = edgeDistance, base = base, height = height))
                data[idx++] = p.x * scale
                data[idx++] = p.y * scale

                if (i != 0) {
                    val p2 = ring[i - 1]
                    // unit(perp(p1 - p2)): outward-facing wall normal
                    val dx = p.x - p2.x
                    val dy = p.y - p2.y
                    val len = kotlin.math.sqrt(dx * dx + dy * dy)
                    val perpX = if (len > 0.0) -dy / len else 0.0
                    val perpY = if (len > 0.0) dx / len else 0.0
                    val dist = (kotlin.math.abs(dx) + kotlin.math.abs(dy)).toInt()
                    // C++ keeps a 16-bit edge distance; reset on overflow
                    if (edgeDistance + dist > Short.MAX_VALUE) edgeDistance = 0

                    val wallBase = vertices.size
                    vertices.add(Vertex(p.x * scale, p.y * scale, perpX, perpY, 0.0, t = 0, edgeDist = edgeDistance, base = base, height = height))
                    vertices.add(Vertex(p.x * scale, p.y * scale, perpX, perpY, 0.0, t = 1, edgeDist = edgeDistance, base = base, height = height))
                    vertices.add(Vertex(p2.x * scale, p2.y * scale, perpX, perpY, 0.0, t = 0, edgeDist = edgeDistance + dist, base = base, height = height))
                    vertices.add(Vertex(p2.x * scale, p2.y * scale, perpX, perpY, 0.0, t = 1, edgeDist = edgeDistance + dist, base = base, height = height))

                    // ┌──────┐
                    // │ 0  1 │ Counter-clockwise winding: (0, 2, 1), (1, 2, 3)
                    // │      │
                    // │ 2  3 │
                    // └──────┘
                    indices.add(wallBase)
                    indices.add(wallBase + 2)
                    indices.add(wallBase + 1)
                    indices.add(wallBase + 1)
                    indices.add(wallBase + 2)
                    indices.add(wallBase + 3)

                    edgeDistance += dist
                }
            }
        }

        // Roof triangulation (earcut), same winding swap as C++:
        // triangles.emplace_back(idx[i], idx[i+2], idx[i+1]).
        val holeIndices = IntArray(valid.size - 1)
        var count = valid[0].size
        for (h in 1 until valid.size) {
            holeIndices[h - 1] = count
            count += valid[h].size
        }
        val tris = Earcut.triangulate(data, holeIndices, 2)
        for (i in 0 until tris.size step 3) {
            indices.add(flatIndices[tris[i]])
            indices.add(flatIndices[tris[i + 2]])
            indices.add(flatIndices[tris[i + 1]])
        }
    }
}

private fun Double.clampToShort(): Short = when {
    isNaN() -> 0
    this <= Short.MIN_VALUE -> Short.MIN_VALUE
    this >= Short.MAX_VALUE -> Short.MAX_VALUE
    else -> roundToInt().toShort()
}
