package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.math.Camera3D
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.math.Vec4
import org.maplibre.kotlin.renderer.SoftwareLayerRenderer
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.layer.StyleLayerFactory
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.TileValue
import org.maplibre.kotlin.tile.VectorTileData

/**
 * End-to-end software rendering of fill-extrusion: 3D camera projection,
 * wall/roof shading, and depth-tested occlusion between buildings.
 */
class FillExtrusionRenderTest {

    private val W = 128
    private val H = 128
    private val EXT = 100.0 // tile extent

    private fun tile(hA: Double, hB: Double, includeB: Boolean = true): VectorTileData {
        // building A (south, nearer camera) and B (north, farther)
        val a = TileFeature(
            1L, FeatureType.POLYGON, mapOf("h" to TileValue.Num(hA)),
            listOf(listOf(
                TilePoint(20.0, 40.0), TilePoint(40.0, 40.0),
                TilePoint(40.0, 60.0), TilePoint(20.0, 60.0),
            )),
        )
        val b = TileFeature(
            2L, FeatureType.POLYGON, mapOf("h" to TileValue.Num(hB)),
            listOf(listOf(
                TilePoint(25.0, 25.0), TilePoint(45.0, 25.0),
                TilePoint(45.0, 45.0), TilePoint(25.0, 45.0),
            )),
        )
        val features = if (includeB) listOf(a, b) else listOf(a)
        return VectorTileData(
            mapOf("buildings" to TileLayer("buildings", 2, 100, features)),
        )
    }

    private fun layer(): org.maplibre.kotlin.style.layer.StyleLayer {
        val parsed = org.maplibre.kotlin.expression.ExpressionParser().parse(
            kotlinx.serialization.json.JsonArray(
                listOf(
                    kotlinx.serialization.json.JsonPrimitive("get"),
                    kotlinx.serialization.json.JsonPrimitive("h"),
                ),
            ),
        )
        val spec = LayerSpec(
            id = "bldg",
            type = LayerType.FillExtrusion,
            source = "map",
            sourceLayer = "buildings",
            paint = mapOf(
                "fill-extrusion-height" to PropertyValue.Expression(parsed!!),
                "fill-extrusion-base" to PropertyValue.Constant(0.0),
                "fill-extrusion-color" to PropertyValue.Constant("#ff0000"),
                "fill-extrusion-opacity" to PropertyValue.Constant(1.0),
                "fill-extrusion-vertical-gradient" to PropertyValue.Constant(false),
            ),
        )
        return StyleLayerFactory.create(spec)!!
    }

    private fun render(hA: Double, hB: Double, pitch: Double, includeB: Boolean = true): ByteArray {
        val renderer = SoftwareLayerRenderer()
        val items = renderer.render(
            layers = listOf(layer()),
            tile = tile(hA, hB, includeB),
            zoom = 16f,
            matrix = Matrix4.identity(),
            camera = SoftwareLayerRenderer.CameraSpec(zoom = 16.0, pitchDeg = pitch),
        )
        assertEquals(1, items.size)
        val item = items[0] as SoftwareLayerRenderer.DrawItem.FillExtrusion
        return renderer.rasterizeFillExtrusion(item, W, H)
    }

    private fun countAlpha(pixels: ByteArray): Int {
        var n = 0
        for (i in 3 until pixels.size step 4) {
            if (pixels[i].toInt() and 0xFF > 0) n++
        }
        return n
    }

    /** Projects a tile-unit point (with elevation) to screen coordinates. */
    private fun project(tileX: Double, tileY: Double, zMeters: Double, pitch: Double): Pair<Int, Int> {
        val m = Camera3D.buildTileMatrix(
            tileOriginX = 0.0, tileOriginY = 0.0, tileSizePx = W.toDouble(),
            ppm = Camera3D.pixelsPerMeter(16.0),
            width = W, height = H, pitchDeg = pitch, bearingDeg = 0.0,
        )
        val clip = m.times(Vec4(tileX.toFloat(), tileY.toFloat(), zMeters.toFloat(), 1.0f))
        val sx = (((clip.x / clip.w + 1.0f) / 2.0f) * W).toInt()
        val sy = ((1.0f - (clip.y / clip.w + 1.0f) / 2.0f) * H).toInt()
        return sx to sy
    }

    private fun toTileUnits(v: Double): Double = v / EXT * 8192.0

    @Test
    fun topDownRendersOnlyRoof() {
        // only building A; at h=0 its roof lies flat on the ground
        val pixels = render(hA = 100.0, hB = 0.0, pitch = 0.0, includeB = false)

        // the roof center projects exactly to the footprint center
        val roofCenter = project(toTileUnits(30.0), toTileUnits(50.0), 100.0, pitch = 0.0)
        val idx = (roofCenter.second * W + roofCenter.first) * 4
        assertTrue(pixels[idx + 3].toInt() and 0xFF > 0, "roof must cover its projected center $roofCenter")

        // the projected roof top edge: nothing drawn above it
        val topEdge = project(toTileUnits(20.0), toTileUnits(40.0), 100.0, pitch = 0.0)
        for (y in 0 until topEdge.second) {
            for (x in 0 until W) {
                val i = (y * W + x) * 4
                assertEquals(0, pixels[i + 3].toInt(), "pixel above roof top must be empty at ($x,$y)")
            }
        }
    }

    @Test
    fun pitchedRendersWalls() {
        // walls only exist when the building has height
        val flat = render(hA = 0.0, hB = 0.0, pitch = 60.0, includeB = false)
        val pitched = render(hA = 100.0, hB = 0.0, pitch = 60.0, includeB = false)
        val nFlat = countAlpha(flat)
        val nPitched = countAlpha(pitched)
        assertTrue(nPitched > nFlat + 300, "height must add wall pixels: $nFlat -> $nPitched")
        // the wall band right of the roof's projected extent (the roof is
        // magnified toward the viewport center, so it never covers the
        // outer part of the wall band): pure wall pixels
        var wallPixels = 0
        for (y in 4 until 48) {
            for (x in 43 until 51) {
                if (pitched[(y * W + x) * 4 + 3].toInt() and 0xFF > 0) wallPixels++
            }
        }
        assertTrue(wallPixels > 150, "pitched render must draw wall pixels, got $wallPixels")
    }

    @Test
    fun perFeatureHeightsElevateDifferently() {
        // A is flat (h=0, no walls) so B's roof is unobstructed
        val low = render(hA = 0.0, hB = 30.0, pitch = 45.0)
        val high = render(hA = 0.0, hB = 60.0, pitch = 45.0)

        // B's roof center, projected at its own elevation
        val roofLow = project(toTileUnits(35.0), toTileUnits(35.0), 30.0, pitch = 45.0)
        val roofHigh = project(toTileUnits(35.0), toTileUnits(35.0), 60.0, pitch = 45.0)
        assertTrue(roofHigh.second < roofLow.second, "taller roof must project higher: $roofLow -> $roofHigh")
        assertTrue(roofHigh.second in 0 until H, "taller roof must stay on-screen: $roofHigh")

        fun brightAt(pixels: ByteArray, px: Pair<Int, Int>): Boolean {
            if (px.first < 0 || px.first >= W || px.second < 0 || px.second >= H) return false
            val idx = (px.second * W + px.first) * 4
            val r = pixels[idx].toInt() and 0xFF
            val al = pixels[idx + 3].toInt() and 0xFF
            return al > 0 && r > 200
        }

        assertTrue(brightAt(low, roofLow), "30m roof must be visible at its projection $roofLow")
        assertTrue(brightAt(high, roofHigh), "60m roof must be visible at its projection $roofHigh")
    }

    @Test
    fun depthOccludesFarBuildingBehindTallNearBuilding() {
        // A tall (100m): A's walls occlude most of B's roof
        val occluded = render(hA = 100.0, hB = 30.0, pitch = 60.0)
        // A flat (0m): B's roof fully visible
        val control = render(hA = 0.0, hB = 30.0, pitch = 60.0)

        // B's roof screen region (probe box around its projected center)
        val roofB = project(toTileUnits(35.0), toTileUnits(35.0), 30.0, pitch = 60.0)

        fun brightCount(pixels: ByteArray): Int {
            var n = 0
            for (dy in -12..12) {
                for (dx in -12..12) {
                    val px = roofB.first + dx
                    val py = roofB.second + dy
                    if (px < 0 || px >= W || py < 0 || py >= H) continue
                    val idx = (py * W + px) * 4
                    val r = pixels[idx].toInt() and 0xFF
                    val al = pixels[idx + 3].toInt() and 0xFF
                    if (al > 0 && r > 245) n++
                }
            }
            return n
        }

        val controlBright = brightCount(control)
        val occludedBright = brightCount(occluded)
        assertTrue(controlBright > 200, "control: B's roof must be largely visible, got $controlBright bright px")
        assertTrue(
            occludedBright < controlBright - 100,
            "tall A must occlude most of B's roof: $occludedBright vs $controlBright bright px",
        )
    }
}
