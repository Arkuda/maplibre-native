package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.renderer.SoftwareLayerRenderer
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.layer.StyleLayerFactory
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.VectorTileData

class SoftwareLayerRendererTest {

    private fun tile(): VectorTileData {
        // polygon covering the whole tile (extent 4096 -> 0..8192 tile units)
        val land = TileFeature(
            1L, FeatureType.POLYGON, emptyMap(),
            listOf(
                listOf(
                    TilePoint(0.0, 0.0),
                    TilePoint(4096.0, 0.0),
                    TilePoint(4096.0, 4096.0),
                    TilePoint(0.0, 4096.0),
                ),
            ),
        )
        // horizontal line through the middle of the tile
        val road = TileFeature(
            2L, FeatureType.LINESTRING, emptyMap(),
            listOf(listOf(TilePoint(0.0, 2048.0), TilePoint(4096.0, 2048.0))),
        )
        return VectorTileData(
            mapOf(
                "landuse" to TileLayer("landuse", 2, 4096, listOf(land)),
                "roads" to TileLayer("roads", 2, 4096, listOf(road)),
            ),
        )
    }

    private fun layers(): List<org.maplibre.kotlin.style.layer.StyleLayer> {
        val fill = LayerSpec(
            id = "land",
            type = LayerType.Fill,
            source = "map",
            sourceLayer = "landuse",
            paint = mapOf(
                "fill-color" to PropertyValue.Constant("#3366ff"),
                "fill-opacity" to PropertyValue.Constant(1.0),
            ),
        )
        val line = LayerSpec(
            id = "road",
            type = LayerType.Line,
            source = "map",
            sourceLayer = "roads",
            paint = mapOf(
                "line-color" to PropertyValue.Constant("#ff0000"),
                // wide line in tile units (8192-wide tile -> 32px framebuffer)
                "line-width" to PropertyValue.Constant(1024.0),
            ),
        )
        return StyleLayerFactory.createAll(listOf(fill, line))
    }

    private fun scaleMatrix(px: Int): Matrix4 {
        // tile units 0..8192 -> NDC -1..1 -> viewport px
        val ndc = Matrix4.translate(-1f, -1f).times(Matrix4.scale(2f / 8192f))
        val viewport = Matrix4.scale(px.toFloat())
        return viewport.times(ndc)
    }

    @Test
    fun renderProducesDrawItems() {
        val renderer = SoftwareLayerRenderer()
        val items = renderer.render(
            layers = layers(),
            tile = tile(),
            zoom = 12f,
            matrix = Matrix4.identity(),
        )
        assertEquals(2, items.size)
        assertTrue(items[0] is SoftwareLayerRenderer.DrawItem.Fill)
        assertTrue(items[1] is SoftwareLayerRenderer.DrawItem.Line)
    }

    @Test
    fun renderSkipsLayersWithoutMatchingTileLayer() {
        val renderer = SoftwareLayerRenderer()
        val layers = StyleLayerFactory.createAll(
            listOf(
                LayerSpec("ghost", LayerType.Fill, source = "map", sourceLayer = "missing"),
            ),
        )
        val items = renderer.render(layers, tile(), 12f, Matrix4.identity())
        assertEquals(0, items.size)
    }

    @Test
    fun renderSkipsInvisibleLayers() {
        val renderer = SoftwareLayerRenderer()
        val layers = StyleLayerFactory.createAll(
            listOf(
                LayerSpec("hidden", LayerType.Fill, source = "map", sourceLayer = "landuse", maxzoom = 10f),
            ),
        )
        val items = renderer.render(layers, tile(), 12f, Matrix4.identity())
        assertEquals(0, items.size)
    }

    @Test
    fun rasterizeFillProducesPixels() {
        val renderer = SoftwareLayerRenderer()
        val matrix = scaleMatrix(32)
        val items = renderer.render(layers(), tile(), 12f, matrix)
        val fill = items.filterIsInstance<SoftwareLayerRenderer.DrawItem.Fill>().first()

        val pixels = renderer.rasterizeFill(fill, 32, 32)
        var count = 0
        for (i in 0 until pixels.size step 4) {
            if (pixels[i + 3].toInt() and 0xFF != 0) count++
        }
        // full-tile polygon -> nearly the whole framebuffer
        assertTrue(count > 700, "expected a filled area, got $count colored pixels")
    }

    @Test
    fun rasterizeLineProducesPixels() {
        val renderer = SoftwareLayerRenderer()
        val matrix = scaleMatrix(64)
        val items = renderer.render(layers(), tile(), 12f, matrix)
        val line = items.filterIsInstance<SoftwareLayerRenderer.DrawItem.Line>().first()

        val pixels = renderer.rasterizeLine(line, 64, 64)
        var count = 0
        for (i in 0 until pixels.size step 4) {
            if (pixels[i + 3].toInt() and 0xFF != 0) count++
        }
        // horizontal line 1024/8192 wide = 8px on a 64px framebuffer, across 64px
        assertTrue(count > 100, "expected a line strip, got $count colored pixels")
    }
}
