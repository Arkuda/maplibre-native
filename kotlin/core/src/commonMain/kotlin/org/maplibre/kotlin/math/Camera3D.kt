package org.maplibre.kotlin.math

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D camera for the software renderer: the CPU twin of the mbgl camera
 * pipeline (TransformState::matrixFor + Camera::getWorldToCamera +
 * getCameraToClipPerspective).
 *
 * Conventions match mbgl:
 *  - tile units 0..8192 (x right, y down)
 *  - elevation in meters, scaled to world pixels by [pixelsPerMeter]
 *  - world pixels at the current zoom (worldSize = 512 * 2^zoom)
 *  - camera looks at the tile center from distance 1.5 * viewportHeight
 *    (fov = 2 * atan(0.5 / 1.5), mbgl's default), pitched from the south
 *    (bearing 0 = looking north)
 *
 * The full matrix chain is proj * view * flipY * model:
 *  - model:  tile units (x, y, elevation m) -> world px (y-down, z scaled)
 *  - flipY:  world y-down -> camera y-up
 *  - view:   lookAt(eye, center, up) with pitch/bearing orbit
 *  - proj:   perspective(fov, aspect, near, far)
 */
object Camera3D {

    /** World size in pixels at a zoom (mbgl tileSize = 512). */
    fun worldSize(zoom: Double): Double = 512.0 * 2.0.pow(zoom)

    /**
     * Pixels per meter at the equator for a zoom. Ported from
     * Camera::getWorldToCamera: worldSize / (cos(lat) * 2PI * EARTH_RADIUS).
     */
    fun pixelsPerMeter(zoom: Double, latitudeDeg: Double = 0.0): Double =
        worldSize(zoom) / (cos(latitudeDeg * PI / 180.0) * 2.0 * PI * 6378137.0)

    /**
     * Builds the full tile matrix for one tile.
     *
     * @param tileOriginX/Y world-pixel origin of the tile (y-down)
     * @param tileSizePx rendered size of the tile in world pixels
     * @param ppm pixels per meter (see [pixelsPerMeter])
     * @param width/height viewport size in pixels
     * @param pitchDeg camera pitch (0 = top-down, mbgl demo default ~60)
     * @param bearingDeg camera bearing (0 = north up)
     * @param centerX/Y viewport center in world pixels (y-down)
     */
    fun buildTileMatrix(
        tileOriginX: Double,
        tileOriginY: Double,
        tileSizePx: Double,
        ppm: Double,
        width: Int,
        height: Int,
        pitchDeg: Double,
        bearingDeg: Double,
        centerX: Double = tileOriginX + tileSizePx / 2.0,
        centerY: Double = tileOriginY + tileSizePx / 2.0,
        near: Double = 0.5,
    ): Matrix4 {
        val s = tileSizePx / 8192.0

        // model: tile units -> world px (y-down), elevation m -> px
        val model = Matrix4.translate(tileOriginX.toFloat(), tileOriginY.toFloat(), 0f)
            .times(Matrix4(floatArrayOf(
                s.toFloat(), 0f, 0f, 0f,
                0f, s.toFloat(), 0f, 0f,
                0f, 0f, ppm.toFloat(), 0f,
                0f, 0f, 0f, 1f,
            )))

        // flip world y-down -> camera y-up
        val flip = Matrix4(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        ))

        // camera: orbit the center at distance d = 1.5 * height, pitched from south
        val d = 1.5 * height
        val pitch = pitchDeg * PI / 180.0
        val bearing = bearingDeg * PI / 180.0
        val eyeX = centerX + sin(pitch) * d * sin(bearing)
        val eyeY = -centerY - sin(pitch) * d * cos(bearing)
        val eyeZ = cos(pitch) * d

        val fov = 2.0 * atan(0.5 / 1.5)
        val far = 10.0 * d
        val proj = perspective(fov, width.toDouble() / height, near, far)
        val view = lookAt(eyeX, eyeY, eyeZ, centerX, -centerY, 0.0, 0.0, 0.0, 1.0)

        return proj.times(view).times(flip).times(model)
    }

    /** Standard perspective projection (OpenGL convention). */
    fun perspective(fovyRad: Double, aspect: Double, near: Double, far: Double): Matrix4 {
        val f = 1.0 / kotlin.math.tan(fovyRad / 2.0)
        val nf = 1.0 / (near - far)
        return Matrix4(floatArrayOf(
            (f / aspect).toFloat(), 0f, 0f, 0f,
            0f, f.toFloat(), 0f, 0f,
            0f, 0f, ((far + near) * nf).toFloat(), -1f,
            0f, 0f, (2f * far * near * nf).toFloat(), 0f,
        ))
    }

    /** Standard look-at view matrix. */
    fun lookAt(
        eyeX: Double, eyeY: Double, eyeZ: Double,
        centerX: Double, centerY: Double, centerZ: Double,
        upX: Double, upY: Double, upZ: Double,
    ): Matrix4 {
        // forward
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        if (fl == 0.0) return Matrix4.identity()
        fx /= fl; fy /= fl; fz /= fl

        // side = f x up
        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        var sl = sqrt(sx * sx + sy * sy + sz * sz)
        if (sl == 0.0) {
            // forward is parallel to up (e.g. looking straight down at
            // pitch 0 with up = +z): pick an arbitrary perpendicular frame
            val (ux2, uy2, uz2) = if (kotlin.math.abs(fz) > 0.99) {
                Triple(0.0, 1.0, 0.0)
            } else {
                Triple(0.0, 0.0, 1.0)
            }
            sx = fy * uz2 - fz * uy2
            sy = fz * ux2 - fx * uz2
            sz = fx * uy2 - fy * ux2
            sl = sqrt(sx * sx + sy * sy + sz * sz)
        }
        sx /= sl; sy /= sl; sz /= sl

        // up = s x f
        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx

        return Matrix4(floatArrayOf(
            sx.toFloat(), ux.toFloat(), -fx.toFloat(), 0f,
            sy.toFloat(), uy.toFloat(), -fy.toFloat(), 0f,
            sz.toFloat(), uz.toFloat(), -fz.toFloat(), 0f,
            (-(sx * eyeX + sy * eyeY + sz * eyeZ)).toFloat(),
            (-(ux * eyeX + uy * eyeY + uz * eyeZ)).toFloat(),
            (fx * eyeX + fy * eyeY + fz * eyeZ).toFloat(),
            1f,
        ))
    }

    private fun Double.pow(e: Double): Double = kotlin.math.exp(kotlin.math.ln(this) * e)
}
