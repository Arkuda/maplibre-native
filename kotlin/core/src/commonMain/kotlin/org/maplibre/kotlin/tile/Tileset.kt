package org.maplibre.kotlin.tile

import kotlin.math.pow
import kotlin.math.round
import org.maplibre.kotlin.util.DEFAULT_MAX_ZOOM
import org.maplibre.kotlin.util.EARTH_RADIUS_M
import org.maplibre.kotlin.util.LatLngBounds
import org.maplibre.kotlin.util.M2PI

/** Tile grid scheme. */
enum class Scheme { XYZ, TMS }

/** DEM raster encoding. */
enum class RasterEncoding { Mapbox, Terrarium }

/** Vector tile encoding. */
enum class VectorEncoding { Mapbox, MLT }

/** A tileset definition (TileJSON-like). Ported from mbgl::Tileset. */
data class Tileset(
    val tiles: List<String> = emptyList(),
    val zoomRange: IntRange = 0..DEFAULT_MAX_ZOOM.toInt(),
    val attribution: String = "",
    val scheme: Scheme = Scheme.XYZ,
    val rasterEncoding: RasterEncoding? = null,
    val vectorEncoding: VectorEncoding? = null,
    val bounds: LatLngBounds? = null,
)

/**
 * Replaces `{tokens}` in a string via a lookup function.
 * Ported from mbgl::util::replaceTokens.
 */
fun replaceTokens(source: String, lookup: (String) -> String?): String {
    val result = StringBuilder(source.length)
    var pos = 0
    val end = source.length
    val reserved = "{}"

    while (pos < end) {
        val brace = source.indexOf('{', pos)
        if (brace < 0) {
            result.append(source, pos, end)
            pos = end
        } else {
            result.append(source, pos, brace)
            pos = brace + 1
            // find closing brace, no nested reserved chars
            var close = pos
            while (close < end && reserved.indexOf(source[close]) < 0) close++
            if (close < end && source[close] == '}') {
                val key = source.substring(pos, close)
                val replacement = lookup(key)
                if (replacement != null) {
                    result.append(replacement)
                } else {
                    result.append('{').append(key).append('}')
                }
                pos = close + 1
            } else {
                result.append(source, brace, close)
                pos = close
            }
        }
    }
    return result.toString()
}

/** Computes the Bing quadkey for a tile. Ported from mbgl::getQuadKey. */
fun getQuadKey(x: Int, y: Int, z: Int): String {
    val quadKey = StringBuilder()
    for (i in z downTo 1) {
        var digit = 0
        val mask = 1 shl (i - 1)
        if ((x and mask) != 0) digit += 1
        if ((y and mask) != 0) digit += 2
        quadKey.append(digit)
    }
    return quadKey.toString()
}

/** Computes the EPSG:3857 bounding box string for a tile. */
fun getTileBBox(x: Int, y: Int, z: Int): String {
    // Alter the y for the Google/OSM tile scheme.
    val yy = (1 shl z) - y - 1
    val min = getMercCoord(x * 256, yy * 256, z)
    val max = getMercCoord((x + 1) * 256, (yy + 1) * 256, z)
    return "${formatDouble(min.first)},${formatDouble(min.second)},${formatDouble(max.first)},${formatDouble(max.second)}"
}

private fun getMercCoord(x: Int, y: Int, z: Int): Pair<Double, Double> {
    val resolution = (M2PI * EARTH_RADIUS_M / 256) / (1 shl z).toDouble()
    return Pair(
        x * resolution - M2PI * EARTH_RADIUS_M / 2,
        y * resolution - M2PI * EARTH_RADIUS_M / 2,
    )
}

/**
 * Formats a double the way rapidjson's Writer::Double does — shortest
 * round-trip representation, never scientific notation.
 */
fun formatDouble(v: Double): String {
    if (v == v.toLong().toDouble()) {
        return v.toLong().toString()
    }
    return shortestDouble(v)
}

private fun shortestDouble(v: Double): String {
    // Round to 6 decimal places (sufficient for tile coordinates) and trim
    val rounded = kotlin.math.round(v * 1e6) / 1e6
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        // simple fixed-format without String.format (not in common)
        val s = rounded.toString()
        if (s.contains('E') || s.contains('e')) {
            // fallback: manual formatting
            formatFixed(rounded, 6)
        } else {
            s
        }
    }
}

private fun formatFixed(v: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals.toDouble())
    val scaled = kotlin.math.round(v * factor)
    val intPart = (scaled / factor).toLong()
    val fracPart = kotlin.math.abs(scaled).toLong() % factor.toLong()
    return buildString {
        append(intPart)
        append('.')
        val fracStr = fracPart.toString()
        repeat(decimals - fracStr.length) { append('0') }
        append(fracStr)
    }
}

/**
 * Builds the URL for a tile from its template.
 * Ported from mbgl::Resource::tile.
 */
fun buildTileUrl(
    urlTemplate: String,
    x: Int,
    y: Int,
    z: Int,
    scheme: Scheme = Scheme.XYZ,
    pixelRatio: Float = 1.0f,
): String {
    var yy = y
    if (scheme == Scheme.TMS) {
        yy = (1 shl z) - y - 1
    }
    val supportsRatio = urlTemplate.contains("{ratio}")
    return replaceTokens(urlTemplate) { token ->
        when (token) {
            "z" -> z.toString()
            "x" -> x.toString()
            "y" -> yy.toString()
            "quadkey" -> getQuadKey(x, yy, z)
            "bbox-epsg-3857" -> getTileBBox(x, yy, z)
            "prefix" -> "${"0123456789abcdef"[x % 16]}${"0123456789abcdef"[yy % 16]}"
            "ratio" -> if (pixelRatio > 1.0f && supportsRatio) "@2x" else ""
            else -> null
        }
    }
}
