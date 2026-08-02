package org.maplibre.kotlin.util

import org.maplibre.kotlin.math.clamp
import org.maplibre.kotlin.math.deg2rad
import org.maplibre.kotlin.math.rad2deg
import org.maplibre.kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.pow

/**
 * Distance measured in meters from the origin of the projected coordinate
 * system.
 */
class ProjectedMeters {
    private val _northing: Double
    private val _easting: Double

    constructor(northing: Double = 0.0, easting: Double = 0.0) {
        require(!northing.isNaN()) { "northing must not be NaN" }
        require(!easting.isNaN()) { "easting must not be NaN" }
        _northing = northing
        _easting = easting
    }

    val northing: Double get() = _northing
    val easting: Double get() = _easting

    override fun equals(other: Any?): Boolean =
        other is ProjectedMeters && _northing == other._northing && _easting == other._easting

    override fun hashCode(): Int = _northing.hashCode() * 31 + _easting.hashCode()

    override fun toString(): String = "ProjectedMeters(northing=$_northing, easting=$_easting)"
}

/**
 * Spherical Mercator projection.
 * https://github.com/openlayers/docs/blob/c7ab69156e17bedc1059d64d7a864b7868e354c1/library/spherical_mercator.rst
 */
object Projection {
    /** Map pixel width at given scale. */
    fun worldSize(scale: Double): Double = scale * TILE_SIZE

    fun getMetersPerPixelAtLatitude(lat: Double, zoom: Double): Double {
        val constrainedZoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM)
        val constrainedScale = 2.0.pow(constrainedZoom)
        val constrainedLatitude = clamp(lat, -LATITUDE_MAX, LATITUDE_MAX)
        return cos(deg2rad(constrainedLatitude)) * M2PI * EARTH_RADIUS_M / worldSize(constrainedScale)
    }

    fun projectedMetersForLatLng(latLng: LatLng): ProjectedMeters {
        val constrainedLatitude = clamp(latLng.latitude, -LATITUDE_MAX, LATITUDE_MAX)
        val constrainedLongitude = clamp(latLng.longitude, -LONGITUDE_MAX, LONGITUDE_MAX)

        val m = 1 - 1e-15
        val f = clamp(sin(deg2rad(constrainedLatitude)), -m, m)

        val easting = deg2rad(EARTH_RADIUS_M * constrainedLongitude)
        val northing = 0.5 * EARTH_RADIUS_M * ln((1 + f) / (1 - f))

        return ProjectedMeters(northing, easting)
    }

    fun latLngForProjectedMeters(projectedMeters: ProjectedMeters): LatLng {
        var latitude = rad2deg(2 * atan(exp(projectedMeters.northing / EARTH_RADIUS_M)) - (PI / 2.0))
        var longitude = rad2deg(projectedMeters.easting) / EARTH_RADIUS_M

        latitude = clamp(latitude, -LATITUDE_MAX, LATITUDE_MAX)
        longitude = clamp(longitude, -LONGITUDE_MAX, LONGITUDE_MAX)

        return LatLng(latitude, longitude)
    }

    fun project(latLng: LatLng, scale: Double): ScreenCoordinate =
        project_(latLng, worldSize(scale))

    /** Returns point on tile. */
    fun project(latLng: LatLng, zoom: Int): ScreenCoordinate =
        project_(latLng, (1 shl zoom).toDouble())

    fun unproject(p: ScreenCoordinate, scale: Double, wrapMode: LatLng.WrapMode = LatLng.WrapMode.Unwrapped): LatLng {
        val p2 = ScreenCoordinate(p.x * DEGREES_MAX / worldSize(scale), p.y * DEGREES_MAX / worldSize(scale))
        return LatLng(
            atan(exp(deg2rad(LONGITUDE_MAX - p2.y))) * DEGREES_MAX / PI - 90.0,
            p2.x - LONGITUDE_MAX,
            wrapMode,
        )
    }

    private fun project_(latLng: LatLng, worldSize: Double): ScreenCoordinate {
        val latitude = clamp(latLng.latitude, -LATITUDE_MAX, LATITUDE_MAX)
        return ScreenCoordinate(
            LONGITUDE_MAX + latLng.longitude,
            LONGITUDE_MAX - rad2deg(ln(tan(PI / 4 + latitude * PI / DEGREES_MAX))),
        ) * (worldSize / DEGREES_MAX)
    }
}
