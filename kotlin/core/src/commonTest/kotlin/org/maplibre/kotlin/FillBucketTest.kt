package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.renderer.bucket.FillBucket
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.TileValue

class FillBucketTest {

    private fun squareLayer(): TileLayer {
        // 4x4 square in extent 4096 space
        val square = listOf(
            TilePoint(0.0, 0.0),
            TilePoint(4.0, 0.0),
            TilePoint(4.0, 4.0),
            TilePoint(0.0, 4.0),
        )
        val feature = TileFeature(
            id = 1L,
            type = FeatureType.POLYGON,
            properties = mapOf("kind" to TileValue.Str("water")),
            geometry = listOf(square),
        )
        return TileLayer("water", version = 2, extent = 4096, features = listOf(feature))
    }

    @Test
    fun squareTriangulatesToTwoTriangles() {
        val bucket = FillBucket()
        bucket.build(squareLayer())

        // 4 corners -> 2 triangles
        assertEquals(4, bucket.vertices.size)
        assertEquals(6, bucket.indices.size)
        assertEquals(2, bucket.triangleCount)
        assertTrue(!bucket.isEmpty)
    }

    @Test
    fun verticesScaledToExtent() {
        val bucket = FillBucket()
        bucket.build(squareLayer())

        // extent 4096 -> EXTENT 8192: scale = 2
        val xs = bucket.vertices.map { it.x }.toSet()
        val ys = bucket.vertices.map { it.y }.toSet()
        assertEquals(setOf<Short>(0, 8), xs)
        assertEquals(setOf<Short>(0, 8), ys)
    }

    @Test
    fun indicesAreInRange() {
        val bucket = FillBucket()
        bucket.build(squareLayer())

        for (i in bucket.indices) {
            assertTrue(i in 0 until bucket.vertices.size, "index $i out of range")
        }
    }

    @Test
    fun filterSkipsFeatures() {
        val bucket = FillBucket()
        bucket.build(squareLayer()) { it.properties["kind"] == TileValue.Str("land") }
        assertTrue(bucket.isEmpty)
    }

    @Test
    fun polygonWithHole() {
        // outer square 8x8, inner square 2x2 hole (centered at 3..5)
        val outer = listOf(
            TilePoint(0.0, 0.0),
            TilePoint(8.0, 0.0),
            TilePoint(8.0, 8.0),
            TilePoint(0.0, 8.0),
        )
        val hole = listOf(
            TilePoint(3.0, 3.0),
            TilePoint(5.0, 3.0),
            TilePoint(5.0, 5.0),
            TilePoint(3.0, 5.0),
        )
        val feature = TileFeature(
            id = 2L,
            type = FeatureType.POLYGON,
            properties = emptyMap(),
            geometry = listOf(outer, hole),
        )
        val layer = TileLayer("land", version = 2, extent = 4096, features = listOf(feature))

        val bucket = FillBucket()
        bucket.build(layer)

        // 8 corners -> at least 6 triangles (area 60 / max tri area ~32)
        assertEquals(8, bucket.vertices.size)
        assertTrue(bucket.triangleCount >= 6, "got ${bucket.triangleCount} triangles")
        assertEquals(bucket.triangleCount * 3, bucket.indices.size)
    }

    @Test
    fun areaPreserved() {
        val bucket = FillBucket()
        bucket.build(squareLayer())

        // sum of triangle areas == square area (4*4 = 16) in tile units
        var area = 0.0
        var i = 0
        while (i < bucket.indices.size) {
            val a = bucket.vertices[bucket.indices[i]]
            val b = bucket.vertices[bucket.indices[i + 1]]
            val c = bucket.vertices[bucket.indices[i + 2]]
            area += Math.abs(
                (b.x - a.x).toDouble() * (c.y - a.y).toDouble() -
                    (c.x - a.x).toDouble() * (b.y - a.y).toDouble(),
            ) / 2.0
            i += 3
        }
        assertEquals(64.0, area, 1e-9) // 8x8 in EXTENT units
    }
}
