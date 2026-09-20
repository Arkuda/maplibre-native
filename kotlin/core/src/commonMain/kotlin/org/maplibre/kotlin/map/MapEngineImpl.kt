package org.maplibre.kotlin.map

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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Platform-neutral typed [MapEngine] implementation using the software
 * rasterizer.
 *
 * Each visible tile is rasterized into its own RGBA buffer, then composited
 * at its screen offset. Android and iOS call this directly from their
 * platform views; no serialized bridge crosses the boundary.
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
    private val sources = LinkedHashMap<String, SourceSpec>()
    private var widthPx = 0
    private var heightPx = 0

    private var pixelRatio = 1.0f
    /**
     * Renders a logical-size viewport into a physical-pixel RGBA buffer.
     *
     * [width] and [height] are logical pixels; [pixelRatio] scales both the
     * framebuffer and the per-tile rasterization resolution.
     */
    fun renderFrame(width: Int, height: Int, pixelRatio: Float = 1.0f): ByteArray {
        require(width >= 0) { "width must be non-negative" }
        require(height >= 0) { "height must be non-negative" }
        require(pixelRatio > 0.0f) { "pixelRatio must be positive" }

        val w = (width * pixelRatio).toInt().coerceAtLeast(1)
        val h = (height * pixelRatio).toInt().coerceAtLeast(1)
        this.widthPx = w
        this.heightPx = h
        this.pixelRatio = pixelRatio
        val out = ByteArray(w * h * 4)

        val layers = layers
        if (layers.isEmpty() || style == null) return out

        val backgroundMatrix = tileMatrix(EXTENT.toFloat())
        val camera = SoftwareLayerRenderer.CameraSpec(
            zoom = zoom,
            pitchDeg = pitch,
            bearingDeg = bearing,
            latitudeDeg = center.latitude,
        )

        // Background layers cover the viewport even when no tile is cached.
        for (item in renderer.render(
            layers = layers,
            tile = VectorTileData(emptyMap()),
            zoom = zoom.toFloat(),
            matrix = backgroundMatrix,
            pixelRatio = pixelRatio,
            camera = camera,
        )) {
            if (item is SoftwareLayerRenderer.DrawItem.Background) {
                composite(out, renderer.rasterizeBackground(item, w, h))
            }
        }

        for (tile in visibleTiles()) {
            val data = cache[tile] ?: continue
            if (data.layers.isEmpty()) continue

            val tileExtent = data.layers.values.firstOrNull()?.extent?.toFloat() ?: EXTENT.toFloat()
            val items = renderer.render(
                layers = layers,
                tile = data,
                zoom = zoom.toFloat(),
                matrix = viewportTileMatrix(tileExtent, tile, w, h),
                pixelRatio = pixelRatio,
                camera = camera,
            )
            for (item in items) {
                rasterizeViewport(item, out, w, h)
            }
        }
        return out
    }

    /** Matrix mapping tile units [0..EXTENT] to the rasterizer's clip space. */
    private fun tileMatrix(extent: Float): Matrix4 = Matrix4(
        floatArrayOf(
            2.0f / extent, 0f, 0f, 0f,
            0f, -2.0f / extent, 0f, 0f,
            0f, 0f, 1f, 0f,
            -1f, 1f, 0f, 1f,
        ),
    )

    private fun viewportTileMatrix(
        extent: Float,
        tile: CanonicalTileID,
        viewportWidth: Int,
        viewportHeight: Int,
    ): Matrix4 {
        val tileSize = (TILE_SIZE * pixelRatio).toFloat()
        val centerTile = Projection.project(center, tile.z.toInt())
        return Matrix4(
            floatArrayOf(
                2f * tileSize / (extent * viewportWidth), 0f, 0f, 0f,
                0f, -2f * tileSize / (extent * viewportHeight), 0f, 0f,
                0f, 0f, 1f, 0f,
                (2.0 * (tile.x.toDouble() - centerTile.x) * tileSize.toDouble() / viewportWidth).toFloat(),
                (-2.0 * (tile.y.toDouble() - centerTile.y) * tileSize.toDouble() / viewportHeight).toFloat(),
                0f, 1f,
            ),
        )
    }

    private fun rasterizeViewport(
        item: SoftwareLayerRenderer.DrawItem,
        target: ByteArray,
        width: Int,
        height: Int,
    ) {
        when (item) {
            is SoftwareLayerRenderer.DrawItem.Background -> Unit
            is SoftwareLayerRenderer.DrawItem.Fill -> composite(target, renderer.rasterizeFill(item, width, height))
            is SoftwareLayerRenderer.DrawItem.Line -> composite(target, renderer.rasterizeLine(item, width, height))
            is SoftwareLayerRenderer.DrawItem.Circle -> composite(target, renderer.rasterizeCircle(item, width, height))
            is SoftwareLayerRenderer.DrawItem.Symbol -> composite(target, renderer.rasterizeSymbol(item, width, height))
            is SoftwareLayerRenderer.DrawItem.Raster,
            is SoftwareLayerRenderer.DrawItem.FillExtrusion -> Unit
        }
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

    // ---- MapEngine API ----------------------------------------------------

    override fun setStyle(style: StyleSpec) {
        this.style = style
        sources.clear()
        sources.putAll(style.sources)
        layers = StyleLayerFactory.createAll(style.layers)
    }

    override fun addSource(source: SourceSpec) {
        sources[source.id] = source
    }

    override fun removeSource(sourceId: String): Boolean = sources.remove(sourceId) != null

    override fun addLayer(layer: LayerSpec) {
        val created = StyleLayerFactory.create(layer) ?: return
        val belowIndex = layer.below?.let { below -> layers.indexOfFirst { it.id == below } } ?: -1
        layers = if (belowIndex < 0) {
            layers + created
        } else {
            layers.toMutableList().apply { add(belowIndex, created) }
        }
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
        val scale = worldScale()
        val c = Projection.project(center, scale)
        return Projection.unproject(
            ScreenCoordinate(
                c.x + screen.x - widthPx / 2.0,
                c.y + screen.y - heightPx / 2.0,
            ),
            scale,
        )
    }

    override fun latLngToScreenCoordinate(latLng: LatLng): ScreenCoordinate {
        val scale = worldScale()
        val p = Projection.project(latLng, scale)
        val c = Projection.project(center, scale)
        return ScreenCoordinate(
            x = p.x - c.x + widthPx / 2.0,
            y = p.y - c.y + heightPx / 2.0,
        )
    }

    override fun visibleTiles(): List<CanonicalTileID> {
        val z = floor(zoom).toInt().coerceIn(0, 22)
        val n = 1 shl z
        val p = Projection.project(center, z)
        val centerTile = CanonicalTileID(
            z.toUByte(),
            floor(p.x).toInt().coerceIn(0, n - 1).toUInt(),
            floor(p.y).toInt().coerceIn(0, n - 1).toUInt(),
        )
        if (widthPx == 0 || heightPx == 0) return listOf(centerTile)

        val tileSizePx = TILE_SIZE * pixelRatio
        val halfWidth = widthPx / (2.0 * tileSizePx)
        val halfHeight = heightPx / (2.0 * tileSizePx)
        val minX = floor(p.x - halfWidth).toInt().coerceIn(0, n - 1)
        val maxX = (ceil(p.x + halfWidth).toInt() - 1).coerceIn(0, n - 1)
        val minY = floor(p.y - halfHeight).toInt().coerceIn(0, n - 1)
        val maxY = (ceil(p.y + halfHeight).toInt() - 1).coerceIn(0, n - 1)

        return buildList((maxX - minX + 1) * (maxY - minY + 1)) {
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    add(CanonicalTileID(z.toUByte(), x.toUInt(), y.toUInt()))
                }
            }
        }
    }

    private fun worldScale(): Double = 2.0.pow(zoom)

    /** Injects a decoded tile into the cache. */
    fun putTile(id: CanonicalTileID, data: VectorTileData) {
        cache[id] = data
    }
}