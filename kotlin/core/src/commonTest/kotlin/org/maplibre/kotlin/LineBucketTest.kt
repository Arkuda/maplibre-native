package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.renderer.bucket.LineBucket
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint

class LineBucketTest {

    private fun roadLayer(): TileLayer {
        val road = listOf(
            TilePoint(0.0, 0.0),
            TilePoint(4096.0, 0.0),
        )
        val feature = TileFeature(
            id = 1L,
            type = FeatureType.LINESTRING,
            properties = emptyMap(),
            geometry = listOf(road),
        )
        return TileLayer("road", version = 2, extent = 4096, features = listOf(feature))
    }

    @Test
    fun buildsFromLineFeatures() {
        val bucket = LineBucket()
        bucket.setLayoutOptions(LineBucket.LayoutOptions())
        bucket.build(roadLayer())

        // 2 points -> 4 skeleton vertices (2 per point, butt caps)
        assertEquals(4, bucket.vertices.size)
        assertEquals(6, bucket.indices.size)
        assertTrue(!bucket.isEmpty)
    }

    @Test
    fun coordinatesScaledToExtent() {
        val bucket = LineBucket()
        bucket.setLayoutOptions(LineBucket.LayoutOptions())
        bucket.build(roadLayer())

        // extent 4096 -> EXTENT 8192: scale = 2, x = 0 and 8192
        val xs = bucket.vertices.map { it.x }.toSet()
        assertEquals(setOf(0, 8192), xs)
    }

    @Test
    fun polygonFeaturesAreSupported() {
        val square = listOf(
            TilePoint(0.0, 0.0),
            TilePoint(100.0, 0.0),
            TilePoint(100.0, 100.0),
            TilePoint(0.0, 100.0),
            TilePoint(0.0, 0.0),
        )
        val feature = TileFeature(2L, FeatureType.POLYGON, emptyMap(), listOf(square))
        val layer = TileLayer("boundary", version = 2, extent = 4096, features = listOf(feature))

        val bucket = LineBucket()
        bucket.setLayoutOptions(LineBucket.LayoutOptions(cap = org.maplibre.kotlin.style.LineCapType.Butt))
        bucket.build(layer)

        assertTrue(!bucket.isEmpty)
        // closed ring generates more than a simple segment
        assertTrue(bucket.vertices.size >= 8, "got ${bucket.vertices.size}")
    }

    @Test
    fun filterSkipsFeatures() {
        val bucket = LineBucket()
        bucket.setLayoutOptions(LineBucket.LayoutOptions())
        bucket.build(roadLayer()) { false }
        assertTrue(bucket.isEmpty)
    }
}
