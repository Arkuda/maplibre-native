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
import org.maplibre.kotlin.style.layer.SymbolLayer
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.VectorTileData

class SymbolLayerTest {

    private fun labelTile(): VectorTileData {
        val city = TileFeature(
            1L, FeatureType.POINT,
            mapOf(
                "name" to org.maplibre.kotlin.tile.TileValue.Str("OSLO"),
                "pop" to org.maplibre.kotlin.tile.TileValue.Num(12.5),
            ),
            listOf(listOf(TilePoint(2048.0, 2048.0))),
        )
        return VectorTileData(
            mapOf("places" to TileLayer("places", 1, 4096, listOf(city))),
        )
    }

    private fun symbolLayer(
        textField: String = "[\"get\", \"name\"]",
        size: Double = 12.0,
        color: String = "#000000",
        opacity: Double = 1.0,
        anchor: String = "center",
    ): SymbolLayer {
        val spec = LayerSpec(
            id = "labels",
            type = LayerType.Symbol,
            source = "map",
            sourceLayer = "places",
            layout = mapOf(
                "text-field" to PropertyValue.Expression(
                    org.maplibre.kotlin.expression.ExpressionParser()
                        .parse(kotlinx.serialization.json.Json.parseToJsonElement(textField))!!,
                ),
                "text-size" to PropertyValue.Constant(size),
                "text-anchor" to PropertyValue.Constant(anchor),
            ),
            paint = mapOf(
                "text-color" to PropertyValue.Constant(color),
                "text-opacity" to PropertyValue.Constant(opacity),
            ),
        )
        return StyleLayerFactory.create(spec) as SymbolLayer
    }

    private fun ndcMatrix(): Matrix4 =
        Matrix4.translate(-1f, -1f).times(Matrix4.scale(2f / 8192f))

    @Test
    fun defaultsMatchCpp() {
        val layer = StyleLayerFactory.create(
            LayerSpec("s", LayerType.Symbol, source = "map", sourceLayer = "places"),
        ) as SymbolLayer
        val ev = layer.evaluate(zoom = 5f)
        assertEquals(16.0, ev.size) // TextSize default 16
        assertEquals(0.5, ev.anchorX) // TextAnchor default center
    }

    @Test
    fun textOfResolvesGetExpression() {
        val layer = symbolLayer()
        val feature = labelTile().getLayer("places")!!.features[0]
        assertEquals("OSLO", layer.textOf(feature))
    }

    @Test
    fun bucketBuildsInstancePerPoint() {
        val layer = symbolLayer()
        val bucket = layer.buildBucket(labelTile().getLayer("places")!!)
        assertEquals(1, bucket.instances.size)
        assertEquals("OSLO", bucket.instances[0].text)
        assertEquals(2048.0, bucket.instances[0].x)
    }

    @Test
    fun rendererEmitsSymbolItem() {
        val renderer = SoftwareLayerRenderer()
        val items = renderer.render(listOf(symbolLayer()), labelTile(), 12f, ndcMatrix())
        assertEquals(1, items.size)
        assertTrue(items[0] is SoftwareLayerRenderer.DrawItem.Symbol)
    }

    @Test
    fun rasterizeSymbolDrawsText() {
        val renderer = SoftwareLayerRenderer()
        val layer = symbolLayer(textField = "[\"get\", \"name\"]", size = 12.0, color = "#ff0000")
        val items = renderer.render(listOf(layer), labelTile(), 12f, ndcMatrix())
        val pixels = renderer.rasterizeSymbol(items[0] as SoftwareLayerRenderer.DrawItem.Symbol, 64, 64)

        var count = 0
        for (i in 0 until pixels.size step 4) {
            if (pixels[i + 3].toInt() and 0xFF > 100) count++
        }
        // "OSLO" at 12px: ~5 chars * ~6px = 30px wide, ~7px tall
        assertTrue(count > 20, "expected text pixels, got $count")

        // some pixels must be red, near the anchor at (16, 48)
        var red = 0
        for (y in 40..56) {
            for (x in 0..32) {
                val idx = (y * 64 + x) * 4
                if (pixels[idx + 3].toInt() and 0xFF > 100) red++
            }
        }
        assertTrue(red > 0, "expected red text near anchor")
    }

    @Test
    fun rasterizeSymbolHonorsAnchor() {
        val renderer = SoftwareLayerRenderer()
        val centered = renderer.rasterizeSymbol(
            renderer.render(
                listOf(symbolLayer(anchor = "center")), labelTile(), 12f, ndcMatrix(),
            )[0] as SoftwareLayerRenderer.DrawItem.Symbol,
            64, 64,
        )
        val top = renderer.rasterizeSymbol(
            renderer.render(
                listOf(symbolLayer(anchor = "top")), labelTile(), 12f, ndcMatrix(),
            )[0] as SoftwareLayerRenderer.DrawItem.Symbol,
            64, 64,
        )

        // count pixels above the anchor row (y=48) for both anchors
        fun above(pixels: ByteArray): Int {
            var n = 0
            for (y in 40 until 48) {
                for (x in 0..32) {
                    if (pixels[(y * 64 + x) * 4 + 3].toInt() and 0xFF > 100) n++
                }
            }
            return n
        }
        // "top" anchor pins the text's top to the point (text below y=48);
        // "center" centers the box on the point, so more pixels sit above y=48
        assertTrue(above(centered) > above(top), "center anchor should draw more above the point than top")
    }

    @Test
    fun rasterizeSymbolAppliesOpacity() {
        val renderer = SoftwareLayerRenderer()
        val layer = symbolLayer(opacity = 0.5)
        val items = renderer.render(listOf(layer), labelTile(), 12f, ndcMatrix())
        val pixels = renderer.rasterizeSymbol(items[0] as SoftwareLayerRenderer.DrawItem.Symbol, 64, 64)
        // find first opaque-ish pixel and check it's ~50% alpha
        var alpha = -1
        for (i in 3 until pixels.size step 4) {
            val a = pixels[i].toInt() and 0xFF
            if (a > 0) {
                alpha = a
                break
            }
        }
        assertTrue(alpha in 100..140, "expected ~128 alpha at 50% opacity, got $alpha")
    }
}
