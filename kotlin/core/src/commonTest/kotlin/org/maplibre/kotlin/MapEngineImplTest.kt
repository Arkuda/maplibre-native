package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.maplibre.kotlin.map.MapEngineImpl
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.SourceSpec
import org.maplibre.kotlin.style.StyleSpec
import org.maplibre.kotlin.tile.CanonicalTileID
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.VectorTileData
import org.maplibre.kotlin.util.LatLng
import org.maplibre.kotlin.util.Projection

class MapEngineImplTest {
    @Test
    fun rendersTypedVectorTileIntoViewport() {
        val engine = MapEngineImpl()
        engine.setStyle(
            StyleSpec(
                sources = mapOf("land" to SourceSpec.Vector("land")),
                layers = listOf(
                    LayerSpec(
                        id = "land",
                        type = LayerType.Fill,
                        source = "land",
                        sourceLayer = "land",
                        paint = mapOf(
                            "fill-color" to PropertyValue.Constant("#3366ff"),
                            "fill-opacity" to PropertyValue.Constant(1.0),
                        ),
                    ),
                ),
            ),
        )
        val tileId = CanonicalTileID(0u, 0u, 0u)
        engine.putTile(
            tileId,
            VectorTileData(
                mapOf(
                    "land" to TileLayer(
                        name = "land",
                        version = 2,
                        extent = 4096,
                        features = listOf(
                            TileFeature(
                                id = 1,
                                type = FeatureType.POLYGON,
                                properties = emptyMap(),
                                geometry = listOf(
                                    listOf(
                                        TilePoint(0.0, 0.0),
                                        TilePoint(4096.0, 0.0),
                                        TilePoint(4096.0, 4096.0),
                                        TilePoint(0.0, 4096.0),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        engine.setCamera(LatLng(0.0, 0.0), 0.0)

        val frame = engine.renderFrame(64, 64)
        val center = (32 * 64 + 32) * 4
        assertEquals(64 * 64 * 4, frame.size)
        assertEquals(0x33, frame[center].toInt() and 0xFF)
        assertEquals(0x66, frame[center + 1].toInt() and 0xFF)
        assertEquals(0xFF, frame[center + 2].toInt() and 0xFF)
        assertEquals(0xFF, frame[center + 3].toInt() and 0xFF)
    }

    @Test
    fun maintainsTypedSourcesAndLayers() {
        val engine = MapEngineImpl()
        engine.setStyle(StyleSpec(sources = mapOf("initial" to SourceSpec.Vector("initial"))))

        assertTrue(engine.removeSource("initial"))
        assertFalse(engine.removeSource("initial"))

        engine.addSource(SourceSpec.GeoJson("runtime"))
        assertTrue(engine.removeSource("runtime"))

        engine.addLayer(LayerSpec("background", LayerType.Background))
        assertTrue(engine.removeLayer("background"))
        assertFalse(engine.removeLayer("background"))
    }

    @Test
    fun usesWorldScaleForViewportAndTileCoverage() {
        val engine = MapEngineImpl()
        val center = LatLng(10.0, 20.0)
        engine.setCamera(center, 2.0)
        engine.renderFrame(400, 300)

        val target = LatLng(10.0, 21.0)
        val worldScale = 4.0
        val expectedX = Projection.project(target, worldScale).x -
            Projection.project(center, worldScale).x + 200.0
        val screen = engine.latLngToScreenCoordinate(target)
        assertEquals(expectedX, screen.x, 1e-9)
        assertEquals(150.0, screen.y, 1e-9)

        val restored = engine.screenCoordinateToLatLng(screen)
        assertEquals(target.latitude, restored.latitude, 1e-9)
        assertEquals(target.longitude, restored.longitude, 1e-9)

        engine.setCamera(LatLng(0.0, 0.0), 1.0)
        engine.renderFrame(1536, 512)
        assertEquals(
            listOf(
                CanonicalTileID(1u, 0u, 0u),
                CanonicalTileID(1u, 1u, 0u),
                CanonicalTileID(1u, 0u, 1u),
                CanonicalTileID(1u, 1u, 1u),
            ),
            engine.visibleTiles(),
        )
    }
}
