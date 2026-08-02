package org.maplibre.kotlin.math

/** Constrains value to the range [min, max]. */
fun clamp(value: Double, min: Double, max: Double): Double = min.coerceAtLeast(max.coerceAtMost(value))

fun clamp(value: Float, min: Float, max: Float): Float = min.coerceAtLeast(max.coerceAtMost(value))

fun clamp(value: Int, min: Int, max: Int): Int = min.coerceAtLeast(max.coerceAtMost(value))

fun clamp(value: Long, min: Long, max: Long): Long = min.coerceAtLeast(max.coerceAtMost(value))

/**
 * Constrains n to the given range (including min, excluding max) via modular
 * arithmetic.
 */
fun wrap(value: Double, min: Double, max: Double): Double {
    if (value >= min && value < max) return value
    if (value == max) return min
    val delta = max - min
    val wrapped = min + (value - min) % delta
    return if (value < min) wrapped + delta else wrapped
}

fun wrap(value: Float, min: Float, max: Float): Float {
    if (value >= min && value < max) return value
    if (value == max) return min
    val delta = max - min
    val wrapped = min + (value - min) % delta
    return if (value < min) wrapped + delta else wrapped
}
