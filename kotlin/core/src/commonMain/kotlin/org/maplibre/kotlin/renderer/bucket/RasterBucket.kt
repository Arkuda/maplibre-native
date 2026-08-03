package org.maplibre.kotlin.renderer.bucket

/**
 * Raster tile image data (decoded pixels). Ported from mbgl::RasterBucket:
 * the C++ version holds a texture + texture-positions quad; here we keep the
 * decoded RGBA pixels directly so the software rasterizer can draw them.
 */
class RasterBucket(
    val width: Int,
    val height: Int,
    /** RGBA, non-premultiplied, row-major, top-down. */
    val pixels: ByteArray,
) {
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}
