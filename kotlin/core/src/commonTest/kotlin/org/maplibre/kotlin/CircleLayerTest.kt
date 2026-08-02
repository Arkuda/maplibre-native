package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.renderer.SoftwareLayerRenderer
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.layer.CircleLayer
import org.maplibre.kotlin.style.layer.StyleLayerFactory
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.VectorTileData

class CircleLayerTest {

    private fun pointTile(): VectorTileData {
        val p = TileFeature(
            1L, FeatureType.POINT, emptyMap(),
            listOf(listOf(TilePoint(2048.0, 2048.0))),
        )
        return VectorTileData(
            mapOf("points" to TileLayer("points", 1, 4096, listOf(p))),
        )
    }

    private fun circleLayer(
        radius: Double = 5.0,
        color: String = "#ff0000",
        strokeWidth: Double = 0.0,
        strokeColor: String = "#000000",
    ): CircleLayer {
        val paint = mutableMapOf<String, PropertyValue>(
            "circle-radius" to PropertyValue.Constant(radius),
            "circle-color" to PropertyValue.Constant(color),
        )
        if (strokeWidth > 0) {
            paint["circle-stroke-width"] = PropertyValue.Constant(strokeWidth)
            paint["circle-stroke-color"] = PropertyValue.Constant(strokeColor)
        }
        val spec = LayerSpec(
            id = "circles",
            type = LayerType.Circle,
            source = "map",
            sourceLayer = "points",
            paint = paint,
        )
        return StyleLayerFactory.create(spec) as CircleLayer
    }

    private fun ndcMatrix(px: Int): Matrix4 {
        // tile units 0..8192 -> NDC -1..1 (the rasterizer's screenX/screenY
        // apply the viewport transform: (x/w+1)/2 * width)
        return Matrix4.translate(-1f, -1f).times(Matrix4.scale(2f / 8192f))
    }

    @Test
    fun defaultsMatchCpp() {
        val layer = StyleLayerFactory.create(
            LayerSpec("c", LayerType.Circle, source = "map", sourceLayer = "points"),
        ) as CircleLayer
        val ev = layer.evaluate(zoom = 5f)
        assertEquals(5.0, ev.radius) // CircleRadius default 5
        assertTrue(ev.scaleWithMap) // CirclePitchScale default "map"
        assertEquals(0.0, ev.strokeWidth)
    }

    @Test
    fun evaluateResolvesProps() {
        val layer = circleLayer(radius = 10.0, color = "#00ff00", strokeWidth = 2.0)
        val ev = layer.evaluate(zoom = 8f)
        assertEquals(10.0, ev.radius)
        assertEquals(2.0, ev.strokeWidth)
        assertTrue(ev.color.g > 0.9f && ev.color.r < 0.1f)
    }

    @Test
    fun bucketBuildsQuadsPerPoint() {
        val layer = circleLayer()
        val bucket = layer.buildBucket(pointTile().getLayer("points")!!)
        // 1 point -> 4 vertices, 2 triangles (6 indices)
        assertEquals(4, bucket.vertices.size)
        assertEquals(6, bucket.indices.size)
        // corners
        assertEquals(-1.0, bucket.vertices[0].extrudeX)
        assertEquals(-1.0, bucket.vertices[0].extrudeY)
        assertEquals(1.0, bucket.vertices[2].extrudeX)
        assertEquals(1.0, bucket.vertices[2].extrudeY)
    }

    @Test
    fun rendererEmitsCircleItem() {
        val renderer = SoftwareLayerRenderer()
        val items = renderer.render(
            listOf(circleLayer()),
            pointTile(),
            zoom = 12f,
            matrix = Matrix4.identity(),
        )
        assertEquals(1, items.size)
        assertTrue(items[0] is SoftwareLayerRenderer.DrawItem.Circle)
    }

    @Test
    fun rasterizeCircleDrawsDisk() {
        val renderer = SoftwareLayerRenderer()
        val layer = circleLayer(radius = 6.0, color = "#ff0000")
        val matrix = ndcMatrix(64)
        val items = renderer.render(listOf(layer), pointTile(), 12f, matrix)
        val pixels = renderer.rasterizeCircle(items[0] as SoftwareLayerRenderer.DrawItem.Circle, 64, 64)

        // point at 2048 tile units -> 2048/8192*64 = 16px (x2 = tile extent 4096 -> 8192)
        var count = 0
        for (i in 0 until pixels.size step 4) {
            if (pixels[i + 3].toInt() and 0xFF > 100) count++
        }
        // circle radius 6 -> area ~113; allow AA tolerance
        assertTrue(count in 60..180, "expected a disk of ~113 px, got $count")

        // center pixel is red (tile 2048 -> NDC (-0.5,-0.5) -> screen (16, 48))
        val cx = 16 * 4
        val r = pixels[64 * 48 * 4 + cx].toInt() and 0xFF
        val g = pixels[64 * 48 * 4 + cx + 1].toInt() and 0xFF
        assertTrue(r > 200 && g < 60, "expected red center, got rgba($r,$g,...)")
    }

    @Test
    fun rasterizeCircleWithStroke() {
        val renderer = SoftwareLayerRenderer()
        val layer = circleLayer(radius = 4.0, color = "#ff0000", strokeWidth = 2.0, strokeColor = "#000000")
        val matrix = ndcMatrix(64)
        val items = renderer.render(listOf(layer), pointTile(), 12f, matrix)
        val pixels = renderer.rasterizeCircle(items[0] as SoftwareLayerRenderer.DrawItem.Circle, 64, 64)

        var count = 0
        for (i in 0 until pixels.size step 4) {
            if (pixels[i + 3].toInt() and 0xFF > 100) count++
        }
        // radius 4 + stroke 2 -> outer radius 6 -> area ~113
        assertTrue(count > 60, "expected a stroked disk, got $count")

        // ring pixel (radius ~5px from center at (16, 48)) should be dark (stroke)
        val cx = 16
        val cy = 48
        val ringX = (cx + 5).coerceAtMost(63)
        val idx = (cy * 64 + ringX) * 4
        val r = pixels[idx].toInt() and 0xFF
        val g = pixels[idx + 1].toInt() and 0xFF
        assertTrue(r < 80 && g < 80, "expected dark stroke at ring, got rgba($r,$g,...)")
    }
}
