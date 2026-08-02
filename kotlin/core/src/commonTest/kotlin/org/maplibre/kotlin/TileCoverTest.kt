package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.tile.CanonicalTileID
import org.maplibre.kotlin.tile.coveringZoomLevel
import org.maplibre.kotlin.tile.tileCount
import org.maplibre.kotlin.tile.tileCover
import org.maplibre.kotlin.util.LatLng
import org.maplibre.kotlin.util.LatLngBounds
import org.maplibre.kotlin.util.Projection

class TileCoverTest {

    @Test
    fun worldAtZoom0() {
        // whole world at z0 => exactly tile 0/0/0
        val tiles = tileCover(LatLngBounds.world(), 0u)
        assertEquals(1, tiles.size)
        val id = tiles[0].canonical
        assertEquals(0u, id.z)
        assertEquals(0u, id.x)
        assertEquals(0u, id.y)
    }

    @Test
    fun halfWorldNorthAtZoom1() {
        // northern hemisphere at z1 => tiles (0,0) and (1,0)
        val north = LatLngBounds.hull(
            LatLng(0.0, -180.0),
            LatLng(85.051128779806604, 180.0),
        )
        val tiles = tileCover(north, 1u)
        val ids = tiles.map { it.canonical }.toSet()
        assertEquals(setOf(CanonicalTileID(1u, 0u, 0u), CanonicalTileID(1u, 1u, 0u)), ids)
    }

    @Test
    fun smallBoundsAtZoom10() {
        // a small area around 0,0 at z10 should produce a handful of tiles
        val bounds = LatLngBounds.hull(
            LatLng(-0.01, -0.01),
            LatLng(0.01, 0.01),
        )
        val tiles = tileCover(bounds, 10u)
        assertTrue(tiles.size in 1..16, "expected 1..16 tiles, got ${tiles.size}")
        // all tiles should be at z10 and around the center of the world
        val center = Projection.project(LatLng(0.0, 0.0), 10)
        for (t in tiles) {
            val id = t.canonical
            assertEquals(10u, id.z)
            // x around 512, y around 512 at z10
            val dx = kotlin.math.abs(id.x.toInt() - center.x.toInt())
            val dy = kotlin.math.abs(id.y.toInt() - center.y.toInt())
            assertTrue(dx <= 1 && dy <= 1, "tile ${id.x}/${id.y} too far from center")
        }
    }

    @Test
    fun coveringZoomVector() {
        // vector source, tile size 512: coveringZoomLevel(5.5) = floor(5.5) = 5
        assertEquals(5, coveringZoomLevel(5.5, raster = false, size = 512))
        // raster source rounds
        assertEquals(6, coveringZoomLevel(5.5, raster = true, size = 512))
    }

    @Test
    fun coveringZoomWithTileSize() {
        // tile size 256 vs world 512: zoom shifts by log2(512/256) = 1
        assertEquals(6, coveringZoomLevel(5.0, raster = false, size = 256))
    }

    @Test
    fun tileCountWorld() {
        assertEquals(1, tileCount(LatLngBounds.world(), 0u))
        assertEquals(4, tileCount(LatLngBounds.world(), 1u))
        assertEquals(16, tileCount(LatLngBounds.world(), 2u))
    }
}
