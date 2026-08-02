package org.maplibre.kotlin.expression

import kotlin.math.abs
import kotlin.math.pow

/**
 * Unit Bezier curve solver. Ported from mbgl::util::UnitBezier (Apple).
 */
class UnitBezier(p1x: Double, p1y: Double, p2x: Double, p2y: Double) {
    private val cx = 3.0 * p1x
    private val bx = 3.0 * (p2x - p1x) - (3.0 * p1x)
    private val ax = 1.0 - (3.0 * p1x) - (3.0 * (p2x - p1x) - (3.0 * p1x))
    private val cy = 3.0 * p1y
    private val by = 3.0 * (p2y - p1y) - (3.0 * p1y)
    private val ay = 1.0 - (3.0 * p1y) - (3.0 * (p2y - p1y) - (3.0 * p1y))

    val p1: Pair<Double, Double> get() = cx / 3.0 to cy / 3.0

    val p2: Pair<Double, Double>
        get() = (bx + (3.0 * cx / 3.0) + cx) / 3.0 to (by + (3.0 * cy / 3.0) + cy) / 3.0

    private fun sampleCurveX(t: Double): Double = ((ax * t + bx) * t + cx) * t

    private fun sampleCurveY(t: Double): Double = ((ay * t + by) * t + cy) * t

    private fun sampleCurveDerivativeX(t: Double): Double = (3.0 * ax * t + 2.0 * bx) * t + cx

    /** Given an x value, find a parametric value it came from. */
    private fun solveCurveX(x: Double, epsilon: Double): Double {
        // First try a few iterations of Newton's method — normally very fast.
        var t2 = x
        var i = 0
        while (i < 8) {
            val x2 = sampleCurveX(t2) - x
            if (abs(x2) < epsilon) return t2
            val d2 = sampleCurveDerivativeX(t2)
            if (abs(d2) < 1e-6) break
            t2 = t2 - x2 / d2
            i++
        }

        // Fall back to the bisection method for reliability.
        var t0 = 0.0
        var t1 = 1.0
        t2 = x

        if (t2 < t0) return t0
        if (t2 > t1) return t1

        while (t0 < t1) {
            val x2 = sampleCurveX(t2)
            if (abs(x2 - x) < epsilon) return t2
            if (x > x2) t0 = t2 else t1 = t2
            t2 = (t1 - t0) * 0.5 + t0
        }

        // Failure.
        return t2
    }

    fun solve(x: Double, epsilon: Double): Double = sampleCurveY(solveCurveX(x, epsilon))

    override fun equals(other: Any?): Boolean =
        other is UnitBezier && cx == other.cx && bx == other.bx && ax == other.ax &&
            cy == other.cy && by == other.by && ay == other.ay

    override fun hashCode(): Int {
        var result = cx.hashCode()
        result = 31 * result + bx.hashCode()
        result = 31 * result + ax.hashCode()
        result = 31 * result + cy.hashCode()
        result = 31 * result + by.hashCode()
        result = 31 * result + ay.hashCode()
        return result
    }
}

/**
 * Interpolation factor for exponential curves. Ported from
 * mbgl::util::interpolationFactor.
 */
fun interpolationFactor(base: Float, range: FloatRange, z: Float): Float {
    val zoomDiff = range.max - range.min
    val zoomProgress = z - range.min
    if (zoomDiff == 0f) {
        return 0f
    } else if (base == 1.0f) {
        return zoomProgress / zoomDiff
    } else {
        return ((base.toDouble().pow(zoomProgress.toDouble()) - 1) /
            (base.toDouble().pow(zoomDiff.toDouble()) - 1)).toFloat()
    }
}

data class FloatRange(val min: Float, val max: Float)

/** Interpolates two numbers. */
fun interpolate(a: Double, b: Double, t: Double): Double = a * (1.0 - t) + b * t

/** Interpolates two floats. */
fun interpolate(a: Float, b: Float, t: Double): Float = (a * (1.0 - t) + b * t).toFloat()

/** Interpolates two colors channel-wise. */
fun interpolate(a: Value.Color, b: Value.Color, t: Double): Value.Color =
    Value.Color(
        interpolate(a.r, b.r, t),
        interpolate(a.g, b.g, t),
        interpolate(a.b, b.b, t),
        interpolate(a.a, b.a, t),
    )

/** Interpolates two number arrays element-wise. */
fun interpolate(a: List<Double>, b: List<Double>, t: Double): List<Double> {
    val size = maxOf(a.size, b.size)
    return List(size) { i ->
        val av = a.getOrNull(i) ?: 0.0
        val bv = b.getOrNull(i) ?: 0.0
        interpolate(av, bv, t)
    }
}

/** Interpolation strategy: exponential (incl. linear, base=1) or cubic-bezier. */
sealed class Interpolator {
    abstract fun interpolationFactor(inputLevels: FloatRange, input: Float): Double

    data class Exponential(val base: Double) : Interpolator() {
        override fun interpolationFactor(inputLevels: FloatRange, input: Float): Double =
            interpolationFactor(base.toFloat(), inputLevels, input).toDouble()
    }

    data class CubicBezier(val ub: UnitBezier) : Interpolator() {
        override fun interpolationFactor(inputLevels: FloatRange, input: Float): Double =
            ub.solve(interpolationFactor(1.0f, inputLevels, input).toDouble(), 1e-6)
    }
}
