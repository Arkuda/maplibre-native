package org.maplibre.kotlin.util

import org.maplibre.kotlin.math.PI

/** Tile size in pixels at zoom level 0. */
const val TILE_SIZE: Double = 512.0
const val TILE_SIZE_F: Float = 512.0f

/** The size of the tile extent in tile coordinates. */
const val EXTENT: Int = 8192
const val EXTENT_F: Float = 8192f

/** 2π */
const val M2PI: Double = PI * 2

/** Earth radius in meters. */
const val EARTH_RADIUS_M: Double = 6378137.0

/** Maximum latitude for Web Mercator projection. */
const val LATITUDE_MAX: Double = 85.051128779806604

/** Maximum longitude (half-world in degrees). */
const val LONGITUDE_MAX: Double = 180.0

/** Full world in degrees. */
const val DEGREES_MAX: Double = 360.0

const val DEFAULT_PITCH_MAX: Double = PI / 3
const val PITCH_MAX: Double = PI

const val MIN_ZOOM: Double = 0.0
const val MAX_ZOOM: Double = 25.5
const val MIN_ZOOM_F: Float = 0.0f
const val MAX_ZOOM_F: Float = 25.5f
const val DEFAULT_MAX_ZOOM: UByte = 22u
