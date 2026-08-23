package org.maplibre.kotlin.android

import org.maplibre.kotlin.map.CameraState
import org.maplibre.kotlin.map.MapEngine
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.renderer.SoftwareLayerRenderer
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.SourceSpec
import org.maplibre.kotlin.style.StyleSpec
import org.maplibre.kotlin.style.layer.StyleLayer
import org.maplibre.kotlin.style.layer.StyleLayerFactory
import org.maplibre.kotlin.tile.CanonicalTileID
import org.maplibre.kotlin.tile.VectorTileData
import org.maplibre.kotlin.util.EXTENT
import org.maplibre.kotlin.util.LatLng
import org.maplibre.kotlin.util.Projection
import org.maplibre.kotlin.util.ScreenCoordinate
import org.maplibre.kotlin.util.TILE_SIZE
import kotlin.math.floor

/**
 * Typed [MapEngine] implementation using the software rasterizer.
 *
 * Each visible tile is rasterized into its own RGBA buffer, then composited
 * at its screen offset (center-anchored). The platform layer uploads the
 * resulting buffer as a GL texture on a fullscreen quad.
 */
class MapEngineImpl : MapEngine {

    private var style: StyleSpec? = null
    private var layers: List<StyleLayer> = emptyList()
    private var center = LatLng(0.0, 0.0)
    private var zoom = 2.0
    private var bearing = 0.0
    private var pitch = 0.0

    private val renderer = SoftwareLayerRenderer()
    private val cache = HashMap<CanonicalTileID, VectorTileData>()
    private var widthPx = 0
    private var heightPx = 0

    /** Renders the current viewport into a screen-sized RGBA buffer. */
    fun renderFrame(widthPx: Int, heightPx: Int, pixelRatio: Float): ByteArray {
        val w = (widthPx * pixelRatio).toInt().coerceAtLeast(1)
        val h = (heightPx * pixelRatio).toInt().coerceAtLeast(1)
        this.widthPx = w
        this.heightPx = h
        val out = ByteArray(w * h * 4)

        val layers = layers
        if (layers.isEmpty() || style == null) return out

        for (tile in visibleTiles()) {
            val data = cache[tile] ?: continue
            if (data.layers.isEmpty()) continue

            val items = renderer.render(
                layers = layers,
                tile = data,
                zoom = zoom.toFloat(),
                matrix = tileMatrix(tile, w, h, pixelRatio),
                pixelRatio = pixelRatio,
                camera = SoftwareLayerRenderer.CameraSpec(
                    zoom = zoom,
                    pitchDeg = pitch,
                    bearingDeg = bearing,
                    latitudeDeg = center.latitude,
                ),
            )

            val tileW = (TILE_SIZE * pixelRatio).toInt()
            val (tilePixels, sx, sy) = rasterizeTile(items, tileW, tile, widthPx, heightPx, pixelRatio)
            blit(out, w, h, tilePixels, tileW, tileW, sx, sy)
        }
        return out
    }

    /** Matrix mapping tile units [0..EXTENT] → screen pixels. */
    private fun tileMatrix(tile: CanonicalTileID, w: Int, h: Int, pixelRatio: Float): Matrix4 {
        val p = Projection.project(center, tile.z.toInt())
        val scale = (TILE_SIZE * pixelRatio).toDouble()

        // Tile origin in world (fractional tile) coords, relative to center.
        val ox = tile.x.toDouble() - p.x
        val oy = tile.y.toDouble() - p.y
        val sx = w / 2.0 + ox * scale
        val sy = h / 2.0 + oy * scale

        // Tile units [0..EXTENT] → [0..TILE_SIZE] px → screen.
        val s = (scale / EXTENT).toFloat()
        return Matrix4.translate(sx.toFloat(), sy.toFloat(), 0f)
            .times(Matrix4.scale(s))
    }

    private fun rasterizeTile(
        items: List<SoftwareLayerRenderer.DrawItem>,
        tileW: Int,
        tile: CanonicalTileID,
        widthPx: Int,
        heightPx: Int,
        pixelRatio: Float,
    ): Triple<ByteArray, Int, Int> {
        val pixels = ByteArray(tileW * tileW * 4)
        for (item in items) {
            when (item) {
                is SoftwareLayerRenderer.DrawItem.Background ->
                    composite(pixels, renderer.rasterizeBackground(item, tileW, tileW))
                is SoftwareLayerRenderer.DrawItem.Fill ->
                    composite(pixels, renderer.rasterizeFill(item, tileW, tileW))
                is SoftwareLayerRenderer.DrawItem.Line ->
                    composite(pixels, renderer.rasterizeLine(item, tileW, tileW))
                is SoftwareLayerRenderer.DrawItem.Circle ->
                    composite(pixels, renderer.rasterizeCircle(item, tileW, tileW))
                is SoftwareLayerRenderer.DrawItem.Symbol ->
                    composite(pixels, renderer.rasterizeSymbol(item, tileW, tileW))
                is SoftwareLayerRenderer.DrawItem.Raster -> Unit
                is SoftwareLayerRenderer.DrawItem.FillExtrusion -> Unit
            }
        }

        // Screen offset of this tile (center-anchored).
        val p = Projection.project(center, tile.z.toInt())
        val world = (1 shl tile.z.toInt()) * TILE_SIZE * pixelRatio
        val sx = (widthPx * pixelRatio / 2.0 + (tile.x.toDouble() - p.x) * world).toInt()
        val sy = (heightPx * pixelRatio / 2.0 + (tile.y.toDouble() - p.y) * world).toInt()
        return Triple(pixels, sx, sy)
    }

    private fun composite(dst: ByteArray, src: ByteArray) {
        for (i in 0 until dst.size step 4) {
            val sa = (src[i + 3].toInt() and 0xFF) / 255f
            if (sa <= 0f) continue
            val da = (dst[i + 3].toInt() and 0xFF) / 255f
            val oa = sa + da * (1f - sa)
            if (oa <= 0f) { dst[i + 3] = 0; continue }
            for (c in 0 until 3) {
                val s = (src[i + c].toInt() and 0xFF) * sa
                val d = (dst[i + c].toInt() and 0xFF) * da * (1f - sa)
                dst[i + c] = ((s + d) / oa).toInt().coerceIn(0, 255).toByte()
            }
            dst[i + 3] = (oa * 255).toInt().coerceIn(0, 255).toByte()
        }
    }

    private fun blit(
        dst: ByteArray, dw: Int, dh: Int,
        src: ByteArray, sw: Int, sh: Int,
        sx: Int, sy: Int,
    ) {
        for (y in 0 until sh) {
            val dy = sy + y
            if (dy < 0 || dy >= dh) continue
            for (x in 0 until sw) {
                val dx = sx + x
                if (dx < 0 || dx >= dw) continue
                val si = (y * sw + x) * 4
                val di = (dy * dw + dx) * 4
                dst[di] = src[si]; dst[di + 1] = src[si + 1]
                dst[di + 2] = src[si + 2]; dst[di + 3] = src[si + 3]
            }
        }
    }

    // ---- MapEngine API ----------------------------------------------------

    override fun setStyle(style: StyleSpec) {
        this.style = style
        this.layers = StyleLayerFactory.createAll(style.layers)
    }

    override fun addSource(source: SourceSpec) {}
    override fun removeSource(sourceId: String): Boolean = false

    override fun addLayer(layer: LayerSpec) {
        StyleLayerFactory.create(layer)?.let { layers = layers + it }
    }
    override fun removeLayer(layerId: String): Boolean {
        val before = layers.size
        layers = layers.filterNot { it.id == layerId }
        return layers.size != before
    }

    override fun setCamera(center: LatLng, zoom: Double, bearing: Double, pitch: Double) {
        this.center = center
        this.zoom = zoom.coerceIn(0.0, 22.0)
        this.bearing = bearing
        this.pitch = pitch.coerceIn(0.0, 60.0)
    }

    override fun getCamera(): CameraState = CameraState(center, zoom, bearing, pitch)

    override fun screenCoordinateToLatLng(screen: ScreenCoordinate): LatLng {
        val c = Projection.project(center, zoom)
        val scale = Projection.worldSize(zoom)
        return Projection.unproject(
            ScreenCoordinate(
                c.x + (screen.x - widthPx / 2.0) / scale,
                c.y - (screen.y - heightPx / 2.0) / scale,
            ),
            zoom,
        )
    }

    override fun latLngToScreenCoordinate(latLng: LatLng): ScreenCoordinate {
        val p = Projection.project(latLng, zoom)
        val c = Projection.project(center, zoom)
        val scale = Projection.worldSize(zoom)
        return ScreenCoordinate(
            x = (p.x - c.x) * scale + widthPx / 2.0,
            y = (c.y - p.y) * scale + heightPx / 2.0,
        )
    }

    override fun visibleTiles(): List<CanonicalTileID> {
        val z = floor(zoom).toInt().coerceIn(0, 22)
        val n = 1 shl z
        val p = Projection.project(center, z)
        val cx = floor(p.x).toInt().coerceIn(0, n - 1)
        val cy = floor(p.y).toInt().coerceIn(0, n - 1)
        return listOf(CanonicalTileID(z.toUByte(), cx.toUInt(), cy.toUInt()))
    }

    /** Injects a decoded tile into the cache. */
    fun putTile(id: CanonicalTileID, data: VectorTileData) {
        cache[id] = data
    }
}