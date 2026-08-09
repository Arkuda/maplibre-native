package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.math.Camera3D
import org.maplibre.kotlin.math.Vec4

class Camera3DTest {

    private fun screen(m: org.maplibre.kotlin.math.Matrix4, x: Double, y: Double, z: Double, w: Int, h: Int): Pair<Double, Double> {
        val clip = m.times(Vec4(x.toFloat(), y.toFloat(), z.toFloat(), 1.0f))
        val sx = ((clip.x / clip.w + 1.0f) / 2.0f) * w
        val sy = (1.0f - (clip.y / clip.w + 1.0f) / 2.0f) * h
        return sx.toDouble() to sy.toDouble()
    }

    @Test
    fun pixelsPerMeterAtEquator() {
        // worldSize(0) / (2PI * EARTH_RADIUS)
        val expected = 512.0 / (2.0 * kotlin.math.PI * 6378137.0)
        assertEquals(expected, Camera3D.pixelsPerMeter(0.0), 1e-12)
        // doubles every zoom
        assertEquals(Camera3D.pixelsPerMeter(1.0), Camera3D.pixelsPerMeter(0.0) * 2.0, 1e-12)
    }

    @Test
    fun topDownProjectsTileToViewport() {
        // tile fills the viewport width; center of tile -> center of screen
        val w = 128
        val h = 128
        val m = Camera3D.buildTileMatrix(0.0, 0.0, w.toDouble(), Camera3D.pixelsPerMeter(16.0), w, h, 0.0, 0.0)
        val (cx, cy) = screen(m, 4096.0, 4096.0, 0.0, w, h)
        assertEquals(w / 2.0, cx, 1.0)
        assertEquals(h / 2.0, cy, 1.0)

        // top-left corner of the tile -> top-left of the viewport
        val (tlx, tly) = screen(m, 0.0, 0.0, 0.0, w, h)
        assertEquals(0.0, tlx, 1.0)
        assertEquals(0.0, tly, 1.0)

        val (brx, bry) = screen(m, 8192.0, 8192.0, 0.0, w, h)
        assertEquals(w.toDouble(), brx, 1.0)
        assertEquals(h.toDouble(), bry, 1.0)
    }

    @Test
    fun pitchShiftsGeometryUp() {
        val w = 128
        val h = 128
        val ppm = Camera3D.pixelsPerMeter(16.0)
        val flat = Camera3D.buildTileMatrix(0.0, 0.0, w.toDouble(), ppm, w, h, 0.0, 0.0)
        val pitched = Camera3D.buildTileMatrix(0.0, 0.0, w.toDouble(), ppm, w, h, 60.0, 0.0)

        // the camera looks at the tile center: it stays at screen center
        val (_, cyFlat) = screen(flat, 4096.0, 4096.0, 0.0, w, h)
        val (_, cyPitched) = screen(pitched, 4096.0, 4096.0, 0.0, w, h)
        assertEquals(cyFlat, cyPitched, 1.0)
        assertEquals(h / 2.0, cyPitched, 1.0)

        // a point north of the tile center: above the center on screen at
        // any pitch (camera looks north, so the far ground is higher)
        val (_, yNorthFlat) = screen(flat, 4096.0, 2048.0, 0.0, w, h)
        val (_, yNorthPitched) = screen(pitched, 4096.0, 2048.0, 0.0, w, h)
        assertTrue(yNorthFlat < cyFlat, "north point must be above center at pitch 0: $yNorthFlat vs $cyFlat")
        assertTrue(yNorthPitched < cyPitched, "north point must be above center at pitch 60: $yNorthPitched vs $cyPitched")

        // a point south of the tile center: below the center
        val (_, ySouthPitched) = screen(pitched, 4096.0, 6144.0, 0.0, w, h)
        assertTrue(ySouthPitched > cyPitched, "south point must be below center at pitch 60: $ySouthPitched vs $cyPitched")
    }

    @Test
    fun elevationProjectsAboveGround() {
        val w = 128
        val h = 128
        val ppm = Camera3D.pixelsPerMeter(16.0)
        val m = Camera3D.buildTileMatrix(0.0, 0.0, w.toDouble(), ppm, w, h, 60.0, 0.0)

        val (gx, gy) = screen(m, 4096.0, 4096.0, 0.0, w, h)
        // 100m at zoom 16 = 83.7 world px
        val (_, hy) = screen(m, 4096.0, 4096.0, 100.0, w, h)
        assertTrue(hy < gy - 10, "elevation must project above ground: $gy -> $hy")
        // taller -> higher on screen
        val (_, hy2) = screen(m, 4096.0, 4096.0, 200.0, w, h)
        assertTrue(hy2 < hy, "taller must project higher: $hy -> $hy2")
    }

    @Test
    fun lookAtMatchesKnownFrame() {
        // camera at (0, 0, 10) looking at origin with up +z
        val m = Camera3D.lookAt(0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val clip = m.times(Vec4(0.0f, 0.0f, 0.0f, 1.0f))
        // origin is 10 units in front of the camera: z = -10
        assertEquals(0.0f, clip.x, 1e-4f)
        assertEquals(0.0f, clip.y, 1e-4f)
        assertEquals(-10.0f, clip.z, 1e-4f)
    }
}
