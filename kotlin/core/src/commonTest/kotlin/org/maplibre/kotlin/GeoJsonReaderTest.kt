package org.maplibre.kotlin

import org.maplibre.kotlin.tile.GeoJsonReader
import org.maplibre.kotlin.tile.GeoJsonTileBuilder
import org.maplibre.kotlin.tile.FeatureType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeoJsonReaderTest {

    @Test
    fun parsePoint() {
        val json = """{"type":"Feature","geometry":{"type":"Point","coordinates":[30.5,50.5]},"properties":{}}"""
        val features = GeoJsonReader.parse(json)
        assertEquals(1, features.size)
        val geom = features[0].geometry as org.maplibre.kotlin.style.GeoJsonGeometry.Point
        assertEquals(30.5, geom.x)
        assertEquals(50.5, geom.y)
    }

    @Test
    fun parsePolygon() {
        val json = """
            {
              "type": "Feature",
              "geometry": {
                "type": "Polygon",
                "coordinates": [[[0,0],[1,0],[1,1],[0,1],[0,0]]]
              },
              "properties": {"name": "test"}
            }
        """.trimIndent()
        val features = GeoJsonReader.parse(json)
        assertEquals(1, features.size)
        val geom = features[0].geometry as org.maplibre.kotlin.style.GeoJsonGeometry.Polygon
        assertEquals(1, geom.rings.size)
        assertEquals(5, geom.rings[0].points.size)
        assertEquals("test", (features[0].properties["name"] as? org.maplibre.kotlin.tile.TileValue.Str)?.value)
    }

    @Test
    fun buildTileFromPoint() {
        // Point at lon=0, lat=0 (center of tile 0/0/0)
        val json = """{"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]},"properties":{}}"""
        val features = GeoJsonReader.parse(json)
        val tile = GeoJsonTileBuilder.buildTile(features, z = 0, x = 0, y = 0)
        
        val layer = tile.getLayer("geojson")
        assertNotNull(layer)
        assertEquals(1, layer.features.size)
        assertEquals(FeatureType.POINT, layer.features[0].type)
        
        // At zoom 0, the whole world is one tile; (0,0) lon/lat maps to (2048, 2048) in 4096 extent
        val geom = layer.features[0].geometry
        assertEquals(1, geom.size)
        assertEquals(1, geom[0].size)
        val pt = geom[0][0]
        assertTrue(pt.x in 2040.0..2056.0, "expected x~2048, got ${pt.x}")
        assertTrue(pt.y in 2040.0..2056.0, "expected y~2048, got ${pt.y}")
    }

    @Test
    fun buildTileFromPolygon() {
        // Small square near lon=0, lat=0
        val json = """
            {"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[-0.1,-0.1],[0.1,-0.1],[0.1,0.1],[-0.1,0.1],[-0.1,-0.1]]]},"properties":{}}
        """.trimIndent()
        val features = GeoJsonReader.parse(json)
        val tile = GeoJsonTileBuilder.buildTile(features, z = 0, x = 0, y = 0)
        
        val layer = tile.getLayer("geojson")
        assertNotNull(layer)
        assertEquals(FeatureType.POLYGON, layer.features[0].type)
        assertEquals(5, layer.features[0].geometry[0].size) // 5 points (closed ring)
    }

    @Test
    fun featureOutsideTileReturnsEmpty() {
        // Point at lon=180, lat=85 (outside the valid Mercator range for tile 0/0/0)
        // At zoom 0, the whole world fits in one tile, so nothing is truly "outside".
        // Use a point that will be filtered by the buffer check.
        // Actually, let's test with a higher zoom where tiles don't cover the whole world.
        val json = """{"type":"Feature","geometry":{"type":"Point","coordinates":[90,0]},"properties":{}}"""
        val features = GeoJsonReader.parse(json)
        // At zoom 2, tile 0/0/0 covers 0-90 lon, so lon=90 should be in tile 1/1/1, not 0/0/0
        val tile = GeoJsonTileBuilder.buildTile(features, z = 2, x = 0, y = 1)
        
        val layer = tile.getLayer("geojson")
        assertNotNull(layer)
        assertEquals(0, layer.features.size) // lon=90 is in tile 2/2/1, not 2/0/1
    }
}
