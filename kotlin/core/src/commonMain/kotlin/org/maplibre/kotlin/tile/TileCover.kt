package org.maplibre.kotlin.tile

import org.maplibre.kotlin.math.ceilLog2
import org.maplibre.kotlin.math.clamp
import org.maplibre.kotlin.math.log2
import org.maplibre.kotlin.util.DEGREES_MAX
import org.maplibre.kotlin.util.LATITUDE_MAX
import org.maplibre.kotlin.util.LatLng
import org.maplibre.kotlin.util.LatLngBounds
import org.maplibre.kotlin.util.Projection
import org.maplibre.kotlin.util.ScreenCoordinate
import org.maplibre.kotlin.util.TILE_SIZE
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Scan-line tile cover algorithm. Ported from mbgl::util::tile_cover.cpp
 * (originally from polymaps src/Layer.js).
 */

private data class Edge(
    val x0: Double, val y0: Double,
    val x1: Double, val y1: Double,
    val dx: Double, val dy: Double,
) {
    /** Builds an edge from two points, ordering them so that y0 <= y1. */
    constructor(a: ScreenCoordinate, b: ScreenCoordinate) : this(
        x0 = if (a.y > b.y) b.x else a.x,
        y0 = if (a.y > b.y) b.y else a.y,
        x1 = if (a.y > b.y) a.x else b.x,
        y1 = if (a.y > b.y) a.y else b.y,
        dx = (if (a.y > b.y) a.x else b.x) - (if (a.y > b.y) b.x else a.x),
        dy = (if (a.y > b.y) a.y else b.y) - (if (a.y > b.y) b.y else a.y),
    )
}

private typealias ScanLine = (x0: Int, x1: Int, y: Int) -> Unit

// scan-line conversion
private fun scanSpans(e0: Edge, e1: Edge, ymin: Int, ymax: Int, scanLine: ScanLine) {
    var e0 = e0
    var e1 = e1
    val y0 = max(ymin.toDouble(), floor(e1.y0))
    val y1 = min(ymax.toDouble(), ceil(e1.y1))

    // sort edges by x-coordinate
    val condition = if (e0.x0 == e1.x0 && e0.y0 == e1.y0) {
        e0.x0 + e1.dy / e0.dy * e0.dx < e1.x1
    } else {
        e0.x1 - e1.dy / e0.dy * e0.dx < e1.x0
    }
    if (condition) {
        val tmp = e0
        e0 = e1
        e1 = tmp
    }

    // scan lines!
    val m0 = e0.dx / e0.dy
    val m1 = e1.dx / e1.dy
    val d0 = if (e0.dx > 0) 1.0 else 0.0 // use y + 1 to compute x0
    val d1 = if (e1.dx < 0) 1.0 else 0.0 // use y + 1 to compute x1
    var y = y0
    while (y < y1) {
        val x0 = m0 * max(0.0, min(e0.dy, y + d0 - e0.y0)) + e0.x0
        val x1 = m1 * max(0.0, min(e1.dy, y + d1 - e1.y0)) + e1.x0
        scanLine(floor(x1).toInt(), ceil(x0).toInt(), y.toInt())
        y++
    }
}

// scan-line conversion
private fun scanTriangle(
    a: ScreenCoordinate,
    b: ScreenCoordinate,
    c: ScreenCoordinate,
    ymin: Int,
    ymax: Int,
    scanLine: ScanLine,
) {
    var ab = Edge(a, b)
    var bc = Edge(b, c)
    var ca = Edge(c, a)

    // sort edges by y-length
    if (ab.dy > bc.dy) {
        val t = ab; ab = bc; bc = t
    }
    if (ab.dy > ca.dy) {
        val t = ab; ab = ca; ca = t
    }
    if (bc.dy > ca.dy) {
        val t = bc; bc = ca; ca = t
    }

    // scan span! scan span!
    if (ab.dy != 0.0) scanSpans(ca, ab, ymin, ymax, scanLine)
    if (bc.dy != 0.0) scanSpans(ca, bc, ymin, ymax, scanLine)
}

/**
 * Computes the set of unwrapped tile ids that cover the given viewport
 * quadrilateral (in tile coordinates at zoom z).
 */
fun tileCover(
    tl: ScreenCoordinate,
    tr: ScreenCoordinate,
    br: ScreenCoordinate,
    bl: ScreenCoordinate,
    c: ScreenCoordinate,
    z: UByte,
): List<UnwrappedTileID> {
    val tiles = 1 shl z.toInt()

    data class ID(val x: Int, val y: Int, val sqDist: Double)

    val t = ArrayList<ID>(8)

    val scanLine: ScanLine = { x0, x1, y ->
        if (y >= 0 && y <= tiles) {
            var x = x0
            while (x < x1) {
                val dx = x + 0.5 - c.x
                val dy = y + 0.5 - c.y
                t.add(ID(x, y, dx * dx + dy * dy))
                x++
            }
        }
    }

    // Divide the screen up in two triangles and scan each of them:
    // \---+
    // | \ |
    // +---\.
    scanTriangle(tl, tr, br, 0, tiles, scanLine)
    scanTriangle(br, bl, tl, 0, tiles, scanLine)

    // Sort first by distance, then by x/y.
    t.sortWith(compareBy({ it.sqDist }, { it.x }, { it.y }))

    // Erase duplicate tile IDs (they typically occur at the common side of both triangles).
    val unique = ArrayList<ID>(t.size)
    for (id in t) {
        if (unique.isEmpty() || unique.last().x != id.x || unique.last().y != id.y) {
            unique.add(id)
        }
    }

    return unique.map { UnwrappedTileID(z, it.x.toLong(), it.y.toLong()) }
}

/** Computes the tiles covered by the given bounds at zoom z. */
fun tileCover(bounds: LatLngBounds, z: UByte): List<UnwrappedTileID> {
    if (bounds.isEmpty || bounds.south > LATITUDE_MAX || bounds.north < -LATITUDE_MAX) {
        return emptyList()
    }
    val clamped = LatLngBounds.hull(
        LatLng(max(bounds.south, -LATITUDE_MAX), bounds.west),
        LatLng(min(bounds.north, LATITUDE_MAX), bounds.east),
    )
    return tileCover(
        Projection.project(clamped.northwest, z.toInt()),
        Projection.project(clamped.northeast, z.toInt()),
        Projection.project(clamped.southeast, z.toInt()),
        Projection.project(clamped.southwest, z.toInt()),
        Projection.project(clamped.center, z.toInt()),
        z,
    )
}

/**
 * Computes the covering zoom level for a source with the given tile size.
 * Ported from mbgl::util::coveringZoomLevel.
 */
fun coveringZoomLevel(zoom: Double, raster: Boolean, size: Int): Int {
    var z = zoom + log2(TILE_SIZE / size.toDouble())
    return if (raster) round(z).toInt() else floor(z).toInt()
}

/**
 * Computes the number of tiles needed to cover the given bounds at zoom.
 * Ported from mbgl::util::tileCount.
 */
fun tileCount(bounds: LatLngBounds, zoom: UByte): Long {
    if (zoom == 0.toUByte()) return 1
    val sw = Projection.project(bounds.southwest, zoom.toInt())
    val ne = Projection.project(bounds.northeast, zoom.toInt())
    val maxTile = 2.0.pow(zoom.toInt())
    val x1 = floor(sw.x)
    val x2 = ceil(ne.x) - 1
    val y1 = clamp(floor(sw.y), 0.0, maxTile - 1)
    val y2 = clamp(floor(ne.y), 0.0, maxTile - 1)

    val dx = if (x1 > x2) (maxTile - x1) + x2 else x2 - x1
    val dy = y1 - y2
    return ((dx + 1) * (dy + 1)).toLong()
}

/** Ceiling of base-2 logarithm. */
fun tileCeilLog2(x: Long): Int = ceilLog2(x)
