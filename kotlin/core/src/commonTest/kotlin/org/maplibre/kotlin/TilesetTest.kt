package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import org.maplibre.kotlin.tile.Scheme
import org.maplibre.kotlin.tile.buildTileUrl
import org.maplibre.kotlin.tile.getQuadKey
import org.maplibre.kotlin.tile.replaceTokens

class TilesetTest {

    @Test
    fun replaceTokensBasic() {
        val out = replaceTokens("https://tiles.example.com/{z}/{x}/{y}.pbf") { token ->
            when (token) {
                "z" -> "5"
                "x" -> "10"
                "y" -> "20"
                else -> null
            }
        }
        assertEquals("https://tiles.example.com/5/10/20.pbf", out)
    }

    @Test
    fun replaceTokensUnknownKeepsBraces() {
        val out = replaceTokens("a{unknown}b") { null }
        assertEquals("a{unknown}b", out)
    }

    @Test
    fun replaceTokensNoTokens() {
        assertEquals("plain-url", replaceTokens("plain-url") { null })
    }

    @Test
    fun quadKeyKnown() {
        // tile 0/0/0 -> "0"
        assertEquals("0", getQuadKey(0, 0, 1))
        // z=2: x=0,y=0 -> "00"; x=1,y=1 -> "11"? (bit interleave: digit = x_bit + 2*y_bit)
        // zoom 2, tile (1,1): bit1: x=0,y=0 -> 0; bit2: x=1,y=1 -> 3 => "03"
        assertEquals("03", getQuadKey(1, 1, 2))
        // tile (3, 3) at z=2 -> bits: (1,1) -> 3, (1,1) -> 3 => "33"
        assertEquals("33", getQuadKey(3, 3, 2))
    }

    @Test
    fun buildUrlXyz() {
        val url = buildTileUrl(
            "https://tiles.example.com/{z}/{x}/{y}.pbf",
            x = 3, y = 5, z = 4,
        )
        assertEquals("https://tiles.example.com/4/3/5.pbf", url)
    }

    @Test
    fun buildUrlTmsFlipsY() {
        // TMS: y' = 2^z - 1 - y; z=4 => 16-1-5 = 10
        val url = buildTileUrl(
            "https://tiles.example.com/{z}/{x}/{y}.pbf",
            x = 3, y = 5, z = 4, scheme = Scheme.TMS,
        )
        assertEquals("https://tiles.example.com/4/3/10.pbf", url)
    }

    @Test
    fun buildUrlQuadKey() {
        val url = buildTileUrl(
            "https://tiles.example.com/{quadkey}.pbf",
            x = 1, y = 1, z = 2,
        )
        assertEquals("https://tiles.example.com/03.pbf", url)
    }

    @Test
    fun buildUrlPrefix() {
        val url = buildTileUrl(
            "https://tiles.example.com/{prefix}/{z}/{x}/{y}.pbf",
            x = 0, y = 0, z = 1,
        )
        // prefix = "00" (x%16=0, y%16=0)
        assertEquals("https://tiles.example.com/00/1/0/0.pbf", url)
    }

    @Test
    fun buildUrlRatio() {
        val url = buildTileUrl(
            "https://tiles.example.com/{z}/{x}/{y}@2x.png",
            x = 1, y = 1, z = 2, pixelRatio = 2.0f,
        )
        // no {ratio} token in template -> unchanged
        assertEquals("https://tiles.example.com/2/1/1@2x.png", url)
    }

    @Test
    fun buildUrlWithRatioToken() {
        val url = buildTileUrl(
            "https://tiles.example.com/{z}/{x}/{y}{ratio}.png",
            x = 1, y = 1, z = 2, pixelRatio = 2.0f,
        )
        assertEquals("https://tiles.example.com/2/1/1@2x.png", url)
        val url1x = buildTileUrl(
            "https://tiles.example.com/{z}/{x}/{y}{ratio}.png",
            x = 1, y = 1, z = 2, pixelRatio = 1.0f,
        )
        assertEquals("https://tiles.example.com/2/1/1.png", url1x)
    }

    @Test
    fun bboxToken() {
        val url = buildTileUrl(
            "https://tiles.example.com/{bbox-epsg-3857}.pbf",
            x = 0, y = 0, z = 0,
        )
        // z0: world bbox in EPSG:3857 (rounded to 6 decimals)
        assertEquals("https://tiles.example.com/-20037508.342789,-20037508.342789,20037508.342789,20037508.342789.pbf", url)
    }
}
