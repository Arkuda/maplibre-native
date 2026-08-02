package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.MvtDecoder
import org.maplibre.kotlin.tile.TileValue

/**
 * Decodes real MapLibre/Mapbox vector tile fixtures from the maplibre-native
 * test suite (copied into commonTest/resources/tiles).
 */
class RealTileTest {

    private fun loadResource(name: String): ByteArray {
        val stream = javaClass.classLoader?.getResourceAsStream("tiles/$name")
            ?: throw IllegalStateException("missing resource tiles/$name")
        return stream.readBytes()
    }

    @Test
    fun decodesRealStreetsTile() {
        val data = loadResource("10-163-395.vector.pbf")
        val tile = MvtDecoder.decode(data)

        assertTrue(tile.layers.isNotEmpty(), "expected at least one layer")
        // the maplibre streets fixture has a road layer
        val roads = tile.getLayer("road") ?: tile.getLayer("roads")
        assertTrue(roads != null, "expected a road layer, got: ${tile.layers.keys}")
        assertTrue(roads!!.features.isNotEmpty(), "expected features in road layer")
        assertEquals(4096, roads.extent)

        // every feature must have decoded geometry and typed properties
        for (f in roads.features.take(20)) {
            assertTrue(f.type != FeatureType.UNKNOWN, "feature type must be known")
            assertTrue(f.geometry.isNotEmpty(), "feature must have geometry")
            for (ring in f.geometry) {
                assertTrue(ring.size >= 1, "ring must have at least one point")
            }
        }
    }

    @Test
    fun decodesRealIssue12432Tile() {
        val data = loadResource("0-0-0.mvt")
        val tile = MvtDecoder.decode(data)
        assertTrue(tile.layers.isNotEmpty(), "expected layers in 0-0-0.mvt")
    }

    @Test
    fun propertiesAreTyped() {
        val data = loadResource("10-163-395.vector.pbf")
        val tile = MvtDecoder.decode(data)
        var sawString = false
        var sawNumber = false
        for (layer in tile.layers.values) {
            for (f in layer.features) {
                for ((k, v) in f.properties) {
                    when (v) {
                        is TileValue.Str -> sawString = true
                        is TileValue.Num -> sawNumber = true
                        else -> {}
                    }
                }
            }
        }
        assertTrue(sawString, "expected at least one string property")
        assertTrue(sawNumber, "expected at least one numeric property")
    }
}
