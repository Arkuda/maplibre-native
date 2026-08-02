package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.renderer.SoftwareLayerRenderer
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.layer.BackgroundLayer
import org.maplibre.kotlin.style.layer.StyleLayerFactory
import org.maplibre.kotlin.tile.VectorTileData

class BackgroundLayerTest {

    private fun bgLayer(
        color: String = "#000000",
        opacity: Double = 1.0,
        minzoom: Float? = null,
        maxzoom: Float? = null,
    ): BackgroundLayer {
        val spec = LayerSpec(
            id = "background",
            type = LayerType.Background,
            minzoom = minzoom,
            maxzoom = maxzoom,
            paint = mapOf(
                "background-color" to PropertyValue.Constant(color),
                "background-opacity" to PropertyValue.Constant(opacity),
            ),
        )
        return StyleLayerFactory.create(spec) as BackgroundLayer
    }

    @Test
    fun defaultValuesMatchCpp() {
        val layer = StyleLayerFactory.create(
            LayerSpec("bg", LayerType.Background),
        ) as BackgroundLayer
        // defaults are resolved by the evaluate() fallbacks, not the spec
        val ev = layer.evaluate(zoom = 5f)
        assertEquals(Color.black(), ev.color)
        assertEquals(1.0, ev.opacity)
    }

    @Test
    fun evaluateResolvesColorAndOpacity() {
        val layer = bgLayer(color = "#ff0000", opacity = 0.5)
        val ev = layer.evaluate(zoom = 8f)
        assertEquals(Color.parse("#ff0000"), ev.color)
        assertEquals(0.5, ev.opacity)
    }

    @Test
    fun zoomRangeClipsVisibility() {
        val layer = bgLayer(minzoom = 3f, maxzoom = 8f)
        assertTrue(!layer.isVisible(2f))
        assertTrue(layer.isVisible(5f))
        assertTrue(!layer.isVisible(8f)) // maxzoom is exclusive
        assertTrue(layer.isVisible(7.99f))
    }

    @Test
    fun factoryCreatesBackgroundLayer() {
        val layer = StyleLayerFactory.create(LayerSpec("bg", LayerType.Background))
        assertTrue(layer is BackgroundLayer)
        // background has no source
        assertEquals(null, layer?.source)
        assertEquals(null, layer?.sourceLayer)
    }

    @Test
    fun rendererEmitsBackgroundItemWithoutTile() {
        val renderer = SoftwareLayerRenderer()
        val layers = listOf(bgLayer(color = "#3366ff"))
        // empty tile: background still renders (no source layer needed)
        val items = renderer.render(layers, VectorTileData(emptyMap()), 10f, org.maplibre.kotlin.math.Matrix4.identity())
        assertEquals(1, items.size)
        val bg = items[0] as SoftwareLayerRenderer.DrawItem.Background
        val expected = Color.parse("#3366ff")!!
        assertTrue(
            kotlin.math.abs(bg.color.r - expected.r) < 0.01f &&
                kotlin.math.abs(bg.color.g - expected.g) < 0.01f &&
                kotlin.math.abs(bg.color.b - expected.b) < 0.01f,
            "expected #3366ff, got ${bg.color}",
        )
        assertEquals(1.0f, bg.opacity)
    }

    @Test
    fun rendererSkipsTransparentBackground() {
        val renderer = SoftwareLayerRenderer()
        val layers = listOf(bgLayer(opacity = 0.0))
        val items = renderer.render(layers, VectorTileData(emptyMap()), 10f, org.maplibre.kotlin.math.Matrix4.identity())
        assertEquals(0, items.size)
    }

    @Test
    fun rasterizeBackgroundFillsAllPixels() {
        val renderer = SoftwareLayerRenderer()
        val layers = listOf(bgLayer(color = "#ff0000", opacity = 1.0))
        val items = renderer.render(layers, VectorTileData(emptyMap()), 10f, org.maplibre.kotlin.math.Matrix4.identity())
        val pixels = renderer.rasterizeBackground(items[0] as SoftwareLayerRenderer.DrawItem.Background, 16, 16)

        var count = 0
        for (i in 0 until pixels.size step 4) {
            if (pixels[i + 3].toInt() and 0xFF != 0) count++
        }
        assertEquals(16 * 16, count, "every pixel must be painted")

        // spot-check the color: r=255, g=0, b=0
        val r = pixels[0].toInt() and 0xFF
        val g = pixels[1].toInt() and 0xFF
        val b = pixels[2].toInt() and 0xFF
        assertTrue(r == 255 && g == 0 && b == 0, "expected pure red, got rgba($r,$g,$b)")
    }

    @Test
    fun rasterizeBackgroundAppliesOpacity() {
        val renderer = SoftwareLayerRenderer()
        val layers = listOf(bgLayer(color = "#ffffff", opacity = 0.5))
        val items = renderer.render(layers, VectorTileData(emptyMap()), 10f, org.maplibre.kotlin.math.Matrix4.identity())
        val pixels = renderer.rasterizeBackground(items[0] as SoftwareLayerRenderer.DrawItem.Background, 8, 8)
        // white 0.5 opacity -> 128 premultiplied
        val r = pixels[0].toInt() and 0xFF
        assertTrue(r in 120..135, "expected ~128 for 50% white, got $r")
    }
}
