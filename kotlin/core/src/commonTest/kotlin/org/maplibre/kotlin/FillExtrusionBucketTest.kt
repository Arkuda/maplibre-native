package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.renderer.bucket.FillExtrusionBucket
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint

class FillExtrusionBucketTest {

    private fun squareLayer(): TileLayer {
        val building = TileFeature(
            1L, FeatureType.POLYGON, emptyMap(),
            listOf(
                listOf(
                    TilePoint(0.0, 0.0),
                    TilePoint(100.0, 0.0),
                    TilePoint(100.0, 100.0),
                    TilePoint(0.0, 100.0),
                ),
            ),
        )
        return TileLayer("buildings", 2, 100, listOf(building))
    }

    @Test
    fun squareProducesWallsAndRoof() {
        val bucket = FillExtrusionBucket()
        bucket.build(squareLayer())

        // 4 roof vertices (one per corner) + 3 edges x 4 wall vertices
        // (closed ring: the closing edge is degenerate, so only n-1 edges,
        // matching the C++ `if (i != 0)` loop)
        assertEquals(4 + 12, bucket.vertices.size)
        // 3 edges x 2 triangles x 3 indices + earcut roof (2 triangles x 3)
        assertEquals(18 + 6, bucket.indices.size)
        assertEquals(8, bucket.triangleCount)

        // roof vertices: t=1, normal up
        val roof = bucket.vertices.filter { it.nz == 1.0 }
        assertEquals(4, roof.size)
        assertTrue(roof.all { it.t == 1 })
        assertTrue(roof.all { it.nx == 0.0 && it.ny == 0.0 })

        // wall vertices: t=0/1 pairs with a horizontal normal, unit length
        val walls = bucket.vertices.filter { it.nz == 0.0 }
        assertEquals(12, walls.size)
        assertEquals(6, walls.count { it.t == 0 })
        assertEquals(6, walls.count { it.t == 1 })
        for (w in walls) {
            val len = kotlin.math.sqrt(w.nx * w.nx + w.ny * w.ny)
            assertEquals(1.0, len, 1e-6, "wall normal must be unit: $w")
        }

        // extrusion from a 100-extent layer: coords scaled to 8192 tile units
        val maxX = bucket.vertices.maxOf { it.x }
        assertEquals(8192.0, maxX, 1e-6)
    }

    @Test
    fun polygonWithHoleKeepsHoleInRoof() {
        val donut = TileFeature(
            1L, FeatureType.POLYGON, emptyMap(),
            listOf(
                listOf(
                    TilePoint(0.0, 0.0), TilePoint(100.0, 0.0),
                    TilePoint(100.0, 100.0), TilePoint(0.0, 100.0),
                ),
                listOf(
                    TilePoint(30.0, 30.0), TilePoint(70.0, 30.0),
                    TilePoint(70.0, 70.0), TilePoint(30.0, 70.0),
                ),
            ),
        )
        val bucket = FillExtrusionBucket()
        bucket.build(TileLayer("buildings", 2, 100, listOf(donut)))

        // 8 roof + 2 rings x 3 edges x 4 wall vertices
        assertEquals(8 + 24, bucket.vertices.size)
        // 2 rings x 3 edges x 2 tris + earcut roof (8 triangles for a donut)
        assertEquals(36 + 24, bucket.indices.size)
    }

    @Test
    fun nonPolygonFeaturesAreSkipped() {
        val line = TileFeature(
            2L, FeatureType.LINESTRING, emptyMap(),
            listOf(listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 100.0))),
        )
        val bucket = FillExtrusionBucket()
        bucket.build(TileLayer("roads", 2, 100, listOf(line)))
        assertTrue(bucket.isEmpty)
    }
}
