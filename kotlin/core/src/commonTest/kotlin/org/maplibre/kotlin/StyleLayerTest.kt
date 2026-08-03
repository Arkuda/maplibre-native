package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.maplibre.kotlin.expression.Value
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.FilterSpec
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.LineCapType
import org.maplibre.kotlin.style.LineJoinType
import org.maplibre.kotlin.style.layer.BackgroundLayer
import org.maplibre.kotlin.style.layer.CircleLayer
import org.maplibre.kotlin.style.layer.RasterLayer
import org.maplibre.kotlin.style.layer.SymbolLayer
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.layer.FillLayer
import org.maplibre.kotlin.style.layer.LineLayer
import org.maplibre.kotlin.style.layer.StyleLayerFactory
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.TileValue

class StyleLayerTest {

    private fun fillSpec(
        paint: Map<String, PropertyValue> = emptyMap(),
        filter: FilterSpec? = null,
    ) = LayerSpec(
        id = "land",
        type = LayerType.Fill,
        source = "map",
        sourceLayer = "landuse",
        paint = paint,
        filter = filter,
    )

    private fun lineSpec(
        paint: Map<String, PropertyValue> = emptyMap(),
        layout: Map<String, PropertyValue> = emptyMap(),
    ) = LayerSpec(
        id = "road",
        type = LayerType.Line,
        source = "map",
        sourceLayer = "roads",
        layout = layout,
        paint = paint,
    )

    private fun polygonFeature(klass: String = "park") = TileFeature(
        id = 1L,
        type = FeatureType.POLYGON,
        properties = mapOf("class" to TileValue.Str(klass), "rank" to TileValue.Num(2.0)),
        geometry = listOf(
            listOf(TilePoint(0.0, 0.0), TilePoint(10.0, 0.0), TilePoint(10.0, 10.0), TilePoint(0.0, 10.0)),
        ),
    )

    @Test
    fun fillDefaultsMatchStyleSpec() {
        val layer = FillLayer(fillSpec())
        val e = layer.evaluate(zoom = 12f)
        assertEquals(Color.black(), e.color)
        assertEquals(1.0, e.opacity, 1e-6)
        assertEquals(0.0, e.outlineColor.a.toDouble(), 1e-6)
    }

    @Test
    fun fillEvaluatesConstants() {
        val layer = FillLayer(
            fillSpec(
                paint = mapOf(
                    "fill-color" to PropertyValue.Constant("#ff0000"),
                    "fill-opacity" to PropertyValue.Constant(0.5),
                ),
            ),
        )
        val e = layer.evaluate(zoom = 12f)
        assertEquals(Color.red(), e.color)
        assertEquals(0.5, e.opacity, 1e-6)
    }

    @Test
    fun fillEvaluatesColorExpression() {
        // interpolate color: zoom 0 -> black, zoom 20 -> red
        val expr = listOf<Any?>(
            "interpolate", listOf("linear"),
            listOf("zoom"),
            0.0, "#000000",
            20.0, "#ff0000",
        )
        val layer = FillLayer(fillSpec(paint = mapOf("fill-color" to PropertyValue.Expression(parse(expr)))))
        val mid = layer.evaluate(zoom = 10f)
        assertEquals(0.5f, mid.color.r, 0.01f)
        val end = layer.evaluate(zoom = 20f)
        assertEquals(1.0f, end.color.r, 0.01f)
    }

    @Test
    fun fillBuildsBucketWithFilter() {
        val layer = FillLayer(fillSpec(filter = FilterSpec.Equals("class", "park")))
        val tileLayer = TileLayer(
            name = "landuse", version = 2, extent = 4096,
            features = listOf(
                polygonFeature("park"),
                polygonFeature("water"),
            ),
        )
        val bucket = layer.buildBucket(tileLayer)
        // only the park polygon is in the bucket
        assertFalse(bucket.isEmpty)
        // 4 vertices per polygon ring (the ring closes back to (0,0))
        assertTrue(bucket.vertices.size >= 4)
    }

    @Test
    fun fillFilterSkipsEverything() {
        val layer = FillLayer(fillSpec(filter = FilterSpec.Equals("class", "nope")))
        val tileLayer = TileLayer("landuse", 2, 4096, listOf(polygonFeature("park")))
        val bucket = layer.buildBucket(tileLayer)
        assertTrue(bucket.isEmpty)
    }

    @Test
    fun lineDefaultsMatchStyleSpec() {
        val layer = LineLayer(lineSpec())
        val e = layer.evaluate(zoom = 12f)
        assertEquals(Color.black(), e.color)
        assertEquals(1.0, e.opacity, 1e-6)
        assertEquals(1.0, e.width, 1e-6)
        assertEquals(0.0, e.gapWidth, 1e-6)
        assertEquals(0.0, e.offset, 1e-6)
        assertEquals(0.0, e.blur, 1e-6)

        val l = layer.layout()
        assertEquals(LineCapType.Butt, l.cap)
        assertEquals(LineJoinType.Miter, l.join)
        assertEquals(2.0, l.miterLimit, 1e-6)
    }

    @Test
    fun lineEvaluatesLayoutAndPaint() {
        val layer = LineLayer(
            lineSpec(
                layout = mapOf(
                    "line-cap" to PropertyValue.Constant("round"),
                    "line-join" to PropertyValue.Constant("bevel"),
                    "line-miter-limit" to PropertyValue.Constant(3.0),
                ),
                paint = mapOf(
                    "line-color" to PropertyValue.Constant("#00ff00"),
                    "line-width" to PropertyValue.Constant(4.0),
                ),
            ),
        )
        val l = layer.layout()
        assertEquals(LineCapType.Round, l.cap)
        assertEquals(LineJoinType.Bevel, l.join)
        assertEquals(3.0, l.miterLimit, 1e-6)

        val e = layer.evaluate(zoom = 10f)
        assertEquals(Color(0f, 1f, 0f, 1f), e.color)
        assertEquals(4.0, e.width, 1e-6)
    }

    @Test
    fun lineEvaluatesZoomInterpolatedWidth() {
        // width: zoom 0 -> 1, zoom 20 -> 21 => at zoom 10, width = 11
        val expr = listOf<Any?>(
            "interpolate", listOf("linear"),
            listOf("zoom"),
            0.0, 1.0,
            20.0, 21.0,
        )
        val layer = LineLayer(lineSpec(paint = mapOf("line-width" to PropertyValue.Expression(parse(expr)))))
        assertEquals(11.0, layer.evaluate(zoom = 10f).width, 0.1)
    }

    @Test
    fun lineBuildsBucketFromFeatures() {
        val layer = LineLayer(lineSpec(layout = mapOf("line-cap" to PropertyValue.Constant("round"))))
        val road = TileFeature(
            id = 2L,
            type = FeatureType.LINESTRING,
            properties = emptyMap(),
            geometry = listOf(listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 0.0))),
        )
        val tileLayer = TileLayer("roads", 2, 4096, listOf(road))
        val bucket = layer.buildBucket(tileLayer)
        assertFalse(bucket.isEmpty)
        // round caps add vertices
        assertTrue(bucket.vertices.size >= 6)
    }

    @Test
    fun visibilityByZoomRange() {
        val layer = FillLayer(
            fillSpec().copy(minzoom = 5f, maxzoom = 10f),
        )
        assertFalse(layer.isVisible(4f))
        assertTrue(layer.isVisible(5f))
        assertTrue(layer.isVisible(9.9f))
        assertFalse(layer.isVisible(10f))
    }

    @Test
    fun factoryCreatesSupportedLayers() {
        val fill = StyleLayerFactory.create(fillSpec())
        val line = StyleLayerFactory.create(lineSpec())
        val bg = StyleLayerFactory.create(LayerSpec("bg", LayerType.Background))
        val circle = StyleLayerFactory.create(LayerSpec("c", LayerType.Circle))
        val symbol = StyleLayerFactory.create(LayerSpec("s", LayerType.Symbol))
        val raster = StyleLayerFactory.create(LayerSpec("r", LayerType.Raster))
        assertNotNull(fill)
        assertTrue(fill is FillLayer)
        assertNotNull(line)
        assertTrue(line is LineLayer)
        assertTrue(bg is BackgroundLayer)
        assertTrue(circle is CircleLayer)
        assertTrue(symbol is SymbolLayer)
        assertTrue(raster is RasterLayer)
    }

    @Test
    fun filterSupportsAllTypes() {
        val f = FilterSpec.AllOf(
            listOf(
                FilterSpec.Equals("class", "park"),
                FilterSpec.In("rank", listOf(1.0, 2.0)),
                FilterSpec.Has("class"),
            ),
        )
        val layer = FillLayer(fillSpec(filter = f))
        assertTrue(layer.matches(polygonFeature("park")))
        assertFalse(layer.matches(polygonFeature("water")))
    }

    private fun parse(expr: List<Any?>): org.maplibre.kotlin.expression.Expression {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(exprToJsonString(expr))
        return org.maplibre.kotlin.expression.ExpressionParser().parse(json)!!
    }

    private fun exprToJsonString(expr: List<Any?>): String {
        fun ser(v: Any?): String = when (v) {
            is String -> "\"$v\""
            is Double -> v.toString()
            is Int -> v.toString()
            is List<*> -> v.joinToString(prefix = "[", postfix = "]", separator = ",") { ser(it) }
            else -> "null"
        }
        return ser(expr)
    }
}
