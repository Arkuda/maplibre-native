package org.maplibre.kotlin.geometry

import org.maplibre.kotlin.math.Point2D

/**
 * Ported from mbgl::geometry::GeometryCoordinates.
 */
data class GeometryCoordinates(val points: List<Point2D<Double>>)
