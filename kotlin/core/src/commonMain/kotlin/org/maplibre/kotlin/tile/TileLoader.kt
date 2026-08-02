package org.maplibre.kotlin.tile

import org.maplibre.kotlin.util.LatLngBounds
import kotlinx.coroutines.flow.Flow

/** Kind of a resource being fetched. Mirrors mbgl::Resource::Kind. */
enum class ResourceKind { Style, Source, Tile, Glyphs, SpriteImage, SpriteJSON, Image }

/**
 * A resource request: what to fetch and from where.
 * Mirrors mbgl::Resource (tile subset).
 */
data class Resource(
    val kind: ResourceKind,
    val url: String,
    val tileData: TileData? = null,
)

/** Tile-specific resource metadata. */
data class TileData(
    val urlTemplate: String,
    val pixelRatio: Int,
    val x: Int,
    val y: Int,
    val z: Int,
)

/** Response of a resource fetch. Mirrors mbgl::Response. */
data class Response(
    val data: ByteArray? = null,
    val error: String? = null,
    val notModified: Boolean = false,
    val mustRevalidate: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Response) return false
        return data?.contentEquals(other.data ?: ByteArray(0)) == true &&
            error == other.error &&
            notModified == other.notModified &&
            mustRevalidate == other.mustRevalidate
    }

    override fun hashCode(): Int {
        var result = data?.contentHashCode() ?: 0
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + notModified.hashCode()
        result = 31 * result + mustRevalidate.hashCode()
        return result
    }
}

/**
 * The resource-fetching abstraction. Mirrors mbgl::FileSource, adapted to
 * Kotlin coroutines.
 */
interface FileSource {
    /** Fetches a resource. Cancellation is cooperative via the coroutine scope. */
    suspend fun request(resource: Resource): Response

    /** True when this source supports cache-only requests. */
    fun supportsCacheOnlyRequests(): Boolean = false
}

/** Callback-style observer of tile loading progress. */
interface TileObserver {
    fun onTileLoaded(id: OverscaledTileID, data: ByteArray)
    fun onTileError(id: OverscaledTileID, error: String)
    fun onTilePlacement(id: OverscaledTileID) {}
}

/**
 * Loads a single tile: builds the URL from the tileset template, fetches it
 * via the [FileSource], and reports the result to the [TileObserver].
 * Mirrors mbgl::TileLoader.
 */
class TileLoader(
    private val id: OverscaledTileID,
    private val tileset: Tileset,
    private val pixelRatio: Float,
    private val fileSource: FileSource?,
    private val observer: TileObserver?,
) {
    private var canceled = false

    /** Builds the resource URL for this tile. */
    fun buildResource(): Resource? {
        val template = tileset.tiles.firstOrNull() ?: return null
        val url = buildTileUrl(
            template,
            id.canonical.x.toInt(),
            id.canonical.y.toInt(),
            id.canonical.z.toInt(),
            tileset.scheme,
            pixelRatio,
        )
        return Resource(
            kind = ResourceKind.Tile,
            url = url,
            tileData = TileData(
                urlTemplate = template,
                pixelRatio = if (pixelRatio > 1.0f) 2 else 1,
                x = id.canonical.x.toInt(),
                y = id.canonical.y.toInt(),
                z = id.canonical.z.toInt(),
            ),
        )
    }

    /** Fetches the tile; invokes the observer on success/error. */
    suspend fun load() {
        if (canceled) return
        val source = fileSource ?: run {
            observer?.onTileError(id, "Can't load tile: no file source.")
            return
        }
        val resource = buildResource() ?: run {
            observer?.onTileError(id, "Can't load tile: no tile URL template.")
            return
        }
        val response = source.request(resource)
        if (canceled) return
        val data = response.data
        if (data != null && response.error == null) {
            observer?.onTileLoaded(id, data)
        } else {
            observer?.onTileError(id, response.error ?: "Unknown error loading tile.")
        }
    }

    fun cancel() {
        canceled = true
    }
}

/**
 * A flow of tiles for the current viewport. Simple implementation: emits the
 * tiles produced by [tileCover] for the current bounds at the given zoom.
 */
class TileStream(
    private val tileset: Tileset,
    private val fileSource: FileSource?,
    private val observer: TileObserver?,
) {
    /** Emits raw tile bytes for all tiles covering [bounds] at [zoom]. */
    fun tiles(bounds: LatLngBounds, zoom: UByte): Flow<Pair<OverscaledTileID, ByteArray>> =
        kotlinx.coroutines.flow.flow {
            val covered = tileCover(bounds, zoom)
            for (tile in covered) {
                val overscaled = OverscaledTileID(zoom, tile.wrap, tile.canonical)
                val loader = TileLoader(overscaled, tileset, 1.0f, fileSource, observer)
                val resource = loader.buildResource() ?: continue
                val response = fileSource?.request(resource) ?: continue
                val data = response.data ?: continue
                emit(overscaled to data)
            }
        }
}
