package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.tile.FileSource
import org.maplibre.kotlin.tile.OverscaledTileID
import org.maplibre.kotlin.tile.Resource
import org.maplibre.kotlin.tile.ResourceKind
import org.maplibre.kotlin.tile.Response
import org.maplibre.kotlin.tile.TileLoader
import org.maplibre.kotlin.tile.Tileset
import kotlinx.coroutines.test.runTest

class TileLoaderTest {

    private class FakeSource(
        private val respond: (Resource) -> Response,
    ) : FileSource {
        val requested = mutableListOf<Resource>()
        override suspend fun request(resource: Resource): Response {
            requested.add(resource)
            return respond(resource)
        }
    }

    @Test
    fun buildsCorrectUrlAndFetches() = runTest {
        val source = FakeSource { Response(data = byteArrayOf(1, 2, 3)) }
        var loaded: ByteArray? = null
        var error: String? = null
        val loader = TileLoader(
            id = OverscaledTileID(4u, 3u, 5u),
            tileset = Tileset(tiles = listOf("https://tiles.example.com/{z}/{x}/{y}.pbf")),
            pixelRatio = 1.0f,
            fileSource = source,
            observer = object : org.maplibre.kotlin.tile.TileObserver {
                override fun onTileLoaded(id: OverscaledTileID, data: ByteArray) {
                    loaded = data
                }

                override fun onTileError(id: OverscaledTileID, msg: String) {
                    error = msg
                }
            },
        )

        loader.load()

        assertEquals(1, source.requested.size)
        val resource = source.requested[0]
        assertEquals(ResourceKind.Tile, resource.kind)
        assertEquals("https://tiles.example.com/4/3/5.pbf", resource.url)
        assertTrue(loaded != null && loaded!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(null, error)
    }

    @Test
    fun reportsErrorOnFailure() = runTest {
        val source = FakeSource { Response(error = "network down") }
        var error: String? = null
        val loader = TileLoader(
            id = OverscaledTileID(2u, 1u, 1u),
            tileset = Tileset(tiles = listOf("https://tiles.example.com/{z}/{x}/{y}.pbf")),
            pixelRatio = 1.0f,
            fileSource = source,
            observer = object : org.maplibre.kotlin.tile.TileObserver {
                override fun onTileLoaded(id: OverscaledTileID, data: ByteArray) {}

                override fun onTileError(id: OverscaledTileID, msg: String) {
                    error = msg
                }
            },
        )

        loader.load()

        assertEquals("network down", error)
    }

    @Test
    fun noFileSourceReportsError() = runTest {
        var error: String? = null
        val loader = TileLoader(
            id = OverscaledTileID(2u, 1u, 1u),
            tileset = Tileset(tiles = listOf("https://tiles.example.com/{z}/{x}/{y}.pbf")),
            pixelRatio = 1.0f,
            fileSource = null,
            observer = object : org.maplibre.kotlin.tile.TileObserver {
                override fun onTileLoaded(id: OverscaledTileID, data: ByteArray) {}

                override fun onTileError(id: OverscaledTileID, msg: String) {
                    error = msg
                }
            },
        )

        loader.load()

        assertTrue(error != null && error!!.contains("no file source"))
    }

    @Test
    fun tmsSchemeYFlipInLoader() = runTest {
        val source = FakeSource { Response(data = byteArrayOf(0)) }
        val loader = TileLoader(
            id = OverscaledTileID(3u, 1u, 2u), // x=1, y=2 at z=3
            tileset = Tileset(tiles = listOf("https://tiles.example.com/{z}/{x}/{y}.pbf"), scheme = org.maplibre.kotlin.tile.Scheme.TMS),
            pixelRatio = 1.0f,
            fileSource = source,
            observer = null,
        )

        loader.load()

        // TMS flip: y' = 2^3 - 1 - 2 = 5
        assertEquals("https://tiles.example.com/3/1/5.pbf", source.requested[0].url)
    }
}
