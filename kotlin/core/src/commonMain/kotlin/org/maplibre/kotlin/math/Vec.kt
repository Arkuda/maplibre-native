package org.maplibre.kotlin.math

import kotlin.math.sqrt
import kotlin.math.floor
import kotlin.math.ceil

/** A 2D point/vector with arithmetic. Mirrors mbgl::Point. */
data class Point2D<T : Number>(val x: T, val y: T) {
    operator fun plus(o: Point2D<T>): Point2D<Double> = Point2D(x.toDouble() + o.x.toDouble(), y.toDouble() + o.y.toDouble())
    operator fun minus(o: Point2D<T>): Point2D<Double> = Point2D(x.toDouble() - o.x.toDouble(), y.toDouble() - o.y.toDouble())
    operator fun times(s: Double): Point2D<Double> = Point2D(x.toDouble() * s, y.toDouble() * s)
    operator fun times(s: Int): Point2D<Double> = Point2D(x.toDouble() * s, y.toDouble() * s)
    operator fun unaryMinus(): Point2D<Double> = Point2D(-x.toDouble(), -y.toDouble())
}

/** Doubles-only vector alias used by the geometry pipeline. */
typealias Vec2 = Point2D<Double>

/** Mirrors mbgl::util::math.hpp helpers. */
object VecMath {

    /** Unit vector (normalized); (0,0) stays (0,0). */
    fun unit(v: Vec2): Vec2 {
        val m = mag(v)
        if (m == 0.0) return Vec2(0.0, 0.0)
        return Vec2(v.x / m, v.y / m)
    }

    /** Perpendicular vector (rotated 90°). mbgl::util::perp. */
    fun perp(v: Vec2): Vec2 = Vec2(-v.y, v.x)

    /** Magnitude (length). */
    fun mag(v: Vec2): Double = sqrt(v.x * v.x + v.y * v.y)

    /** Euclidean distance between two points. */
    fun dist(a: Vec2, b: Vec2): Double = mag(a - b)

    /** Rounds to nearest, half away from zero (mbgl::util::round). */
    fun round(v: Double): Double = if (v >= 0.0) floor(v + 0.5) else ceil(v - 0.5)

    /** Component-wise round of a vector. */
    fun round(v: Vec2): Vec2 = Vec2(round(v.x), round(v.y))
}
