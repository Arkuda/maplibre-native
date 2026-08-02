package org.maplibre.kotlin.renderer.bucket

import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint

/**
 * A circle around each point feature. Each point becomes a 2-triangle quad;
 * the extrusion (-1..1) is stored per-vertex so the shader can expand the
 * quad to the evaluated radius.
 *
 * Ported from mbgl::CircleBucket / mbgl::layout::addCircle.
 * Note: the C++ packs (x*2 + (ex+1)/2) into a single int16; here the
 * position and extrusion are kept as separate typed fields (no bit-packing).
 */
class CircleBucket {

    class Vertex(
        val x: Double,
        val y: Double,
        /** Extrusion normal, -1 or +1 per axis. */
        val extrudeX: Double,
        val extrudeY: Double,
    )

    val vertices = mutableListOf<Vertex>()
    val indices = mutableListOf<Int>()

    val isEmpty: Boolean get() = indices.isEmpty()

    /** Builds circle quads for all point features of a tile layer. */
    fun build(tileLayer: TileLayer, filter: (TileFeature) -> Boolean) {
        for (feature in tileLayer.features) {
            if (!filter(feature)) continue
            for (ring in feature.geometry) {
                for (point in ring) {
                    addPoint(point)
                }
            }
        }
    }

    private fun addPoint(point: TilePoint) {
        val x = point.x
        val y = point.y

        val base = vertices.size
        // ┌─────────┐
        // │ 4     3 │
        // │         │
        // │ 1     2 │
        // └─────────┘
        vertices.add(Vertex(x, y, -1.0, -1.0)) // 1
        vertices.add(Vertex(x, y, 1.0, -1.0))  // 2
        vertices.add(Vertex(x, y, 1.0, 1.0))   // 3
        vertices.add(Vertex(x, y, -1.0, 1.0))  // 4

        // 1, 2, 3
        indices.add(base)
        indices.add(base + 1)
        indices.add(base + 2)
        // 1, 4, 3
        indices.add(base)
        indices.add(base + 3)
        indices.add(base + 2)
    }
}
