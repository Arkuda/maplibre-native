package org.maplibre.kotlin.util

import org.maplibre.kotlin.math.deg2rad
import org.maplibre.kotlin.math.rad2deg
import org.maplibre.kotlin.math.clamp
import org.maplibre.kotlin.math.wrap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.max
import kotlin.math.atan
import kotlin.math.tan
import kotlin.math.sin
import kotlin.math.cos

/** A point in screen (pixel) coordinates. */
data class ScreenCoordinate(val x: Double, val y: Double) {
    operator fun plus(other: ScreenCoordinate) = ScreenCoordinate(x + other.x, y + other.y)
    operator fun minus(other: ScreenCoordinate) = ScreenCoordinate(x - other.x, y - other.y)
    operator fun times(scale: Double) = ScreenCoordinate(x * scale, y * scale)
    operator fun div(scale: Double) = ScreenCoordinate(x / scale, y / scale)
}

/** A latitude/longitude pair. */
class LatLng {
    enum class WrapMode { Unwrapped, Wrapped }

    private var lat: Double
    private var lon: Double

    constructor(lat: Double = 0.0, lon: Double = 0.0, mode: WrapMode = WrapMode.Unwrapped) {
        require(!lat.isNaN()) { "latitude must not be NaN" }
        require(!lon.isNaN()) { "longitude must not be NaN" }
        require(abs(lat) <= 90.0) { "latitude must be between -90 and 90" }
        require(lon.isFinite()) { "longitude must not be infinite" }
        this.lat = lat
        this.lon = lon
        if (mode == WrapMode.Wrapped) wrap()
    }

    val latitude: Double get() = lat
    val longitude: Double get() = lon

    fun wrapped(): LatLng = LatLng(lat, lon, WrapMode.Wrapped)

    fun wrap() {
        lon = wrap(lon, -LONGITUDE_MAX, LONGITUDE_MAX)
    }

    /**
     * If the distance from start to end longitudes is between half and full
     * world, unwrap the start longitude to ensure the shortest path is taken.
     */
    fun unwrapForShortestPath(end: LatLng): LatLng {
        val delta = abs(end.lon - lon)
        if (delta <= LONGITUDE_MAX || delta >= DEGREES_MAX) return this
        return if (lon > 0 && end.lon < 0) {
            LatLng(lat, lon - DEGREES_MAX)
        } else if (lon < 0 && end.lon > 0) {
            LatLng(lat, lon + DEGREES_MAX)
        } else {
            this
        }
    }

    override fun equals(other: Any?): Boolean =
        other is LatLng && lat == other.lat && lon == other.lon

    override fun hashCode(): Int = lat.hashCode() * 31 + lon.hashCode()

    override fun toString(): String = "LatLng($lat, $lon)"
}

/** A bounding box in latitude/longitude. */
class LatLngBounds private constructor(
    private val sw: LatLng,
    private val ne: LatLng,
    private val bounded: Boolean = true,
) {
    companion object {
        /** A bounds covering the entire (unwrapped) world. */
        fun world(): LatLngBounds = LatLngBounds(LatLng(-90.0, -180.0), LatLng(90.0, 180.0))

        /** The bounds consisting of the single point. */
        fun singleton(a: LatLng): LatLngBounds = LatLngBounds(a, a)

        /** The convex hull of two points; the smallest bounds that contains both. */
        fun hull(a: LatLng, b: LatLng): LatLngBounds {
            val bounds = LatLngBounds(a, a)
            return bounds.extend(b)
        }

        /**
         * A bounds that may serve as the identity element for the extend
         * operation.
         */
        fun empty(): LatLngBounds {
            val bounds = world()
            return LatLngBounds(bounds.ne, bounds.sw)
        }
    }

    /** An infinite bound; constrain returns its input unmodified. */
    constructor() : this(LatLng(-90.0, -180.0), LatLng(90.0, 180.0), bounded = false)

    val valid: Boolean
        get() = (sw.latitude <= ne.latitude) && (sw.longitude <= ne.longitude)

    val south: Double get() = sw.latitude
    val west: Double get() = sw.longitude
    val north: Double get() = ne.latitude
    val east: Double get() = ne.longitude

    val southwest: LatLng get() = sw
    val northeast: LatLng get() = ne
    val southeast: LatLng get() = LatLng(south, east)
    val northwest: LatLng get() = LatLng(north, west)

    val center: LatLng
        get() = LatLng((sw.latitude + ne.latitude) / 2, (sw.longitude + ne.longitude) / 2)

    fun constrain(p: LatLng): LatLng {
        val lat = if (p.latitude < south) south else if (p.latitude > north) north else p.latitude
        val lon = if (p.longitude < west) west else if (p.longitude > east) east else p.longitude
        return LatLng(lat, lon)
    }

    fun extend(point: LatLng): LatLngBounds {
        val newSw = LatLng(min(point.latitude, sw.latitude), min(point.longitude, sw.longitude))
        val newNe = LatLng(max(point.latitude, ne.latitude), max(point.longitude, ne.longitude))
        return LatLngBounds(newSw, newNe)
    }

    fun extend(bounds: LatLngBounds): LatLngBounds {
        var b = extend(bounds.sw)
        b = b.extend(bounds.ne)
        return b
    }

    val isEmpty: Boolean
        get() = sw.latitude > ne.latitude || sw.longitude > ne.longitude

    val crossesAntimeridian: Boolean
        get() = (sw.wrapped().longitude > ne.wrapped().longitude)

    fun contains(point: LatLng, wrapMode: LatLng.WrapMode = LatLng.WrapMode.Unwrapped): Boolean =
        containsLatitude(point.latitude) && containsLongitude(point.longitude, wrapMode)

    fun contains(area: LatLngBounds, wrapMode: LatLng.WrapMode = LatLng.WrapMode.Unwrapped): Boolean =
        contains(area.sw, wrapMode) && contains(area.ne, wrapMode)

    fun intersects(area: LatLngBounds, wrapMode: LatLng.WrapMode = LatLng.WrapMode.Unwrapped): Boolean =
        contains(area.sw, wrapMode) || contains(area.ne, wrapMode) || area.contains(this, wrapMode)

    private fun containsLatitude(latitude: Double): Boolean =
        latitude >= sw.latitude && latitude <= ne.latitude

    private fun containsLongitude(longitude: Double, wrapMode: LatLng.WrapMode): Boolean {
        if (wrapMode == LatLng.WrapMode.Wrapped) {
            if (longitude >= west && longitude <= east) return true
            return longitude + DEGREES_MAX >= west && longitude + DEGREES_MAX <= east
        }
        return longitude >= west && longitude <= east
    }

    override fun equals(other: Any?): Boolean {
        if (other !is LatLngBounds) return false
        return (!bounded && !other.bounded) ||
            (bounded && other.bounded && sw == other.sw && ne == other.ne)
    }

    override fun hashCode(): Int = sw.hashCode() * 31 + ne.hashCode()

    override fun toString(): String = "LatLngBounds($sw, $ne)"
}

/** The distance on each side between a rectangle and a rectangle within. */
class EdgeInsets {
    private val _top: Double
    private val _left: Double
    private val _bottom: Double
    private val _right: Double

    constructor(top: Double = 0.0, left: Double = 0.0, bottom: Double = 0.0, right: Double = 0.0) {
        require(!top.isNaN()) { "top must not be NaN" }
        require(!left.isNaN()) { "left must not be NaN" }
        require(!bottom.isNaN()) { "bottom must not be NaN" }
        require(!right.isNaN()) { "right must not be NaN" }
        _top = top
        _left = left
        _bottom = bottom
        _right = right
    }

    val top: Double get() = _top
    val left: Double get() = _left
    val bottom: Double get() = _bottom
    val right: Double get() = _right

    val isFlush: Boolean get() = _top == 0.0 && _left == 0.0 && _bottom == 0.0 && _right == 0.0

    operator fun plus(o: EdgeInsets): EdgeInsets = EdgeInsets(
        _top + o._top,
        _left + o._left,
        _bottom + o._bottom,
        _right + o._right,
    )

    operator fun plusAssign(o: EdgeInsets) {
        // mirror C++ mutating operator; return new via plus
    }

    fun getCenter(width: Int, height: Int): ScreenCoordinate {
        val left = left + (width - left - right) / 2.0
        val top = top + (height - top - bottom) / 2.0
        return ScreenCoordinate(left, top)
    }

    override fun equals(other: Any?): Boolean =
        other is EdgeInsets &&
            _top == other._top && _left == other._left && _bottom == other._bottom && _right == other._right

    override fun hashCode(): Int {
        var result = _top.hashCode()
        result = 31 * result + _left.hashCode()
        result = 31 * result + _bottom.hashCode()
        result = 31 * result + _right.hashCode()
        return result
    }
}

data class LatLngAltitude(val location: LatLng = LatLng(0.0, 0.0), val altitude: Double = 0.0)
