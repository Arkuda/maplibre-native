package org.maplibre.kotlin.math

import kotlin.math.ln

/** Converts degrees to radians. */
fun deg2rad(deg: Double): Double = deg * PI / 180.0

/** Converts degrees to radians (float). */
fun deg2radf(deg: Float): Float = deg * PI_F / 180.0f

/** Converts radians to degrees. */
fun rad2deg(rad: Double): Double = rad * 180.0 / PI

/** Converts radians to degrees (float). */
fun rad2degf(rad: Float): Float = rad * 180.0f / PI_F

/** Natural logarithm, base 2 of a value. */
fun log2(x: Double): Double = ln(x) / ln(2.0)

const val PI: Double = 3.14159265358979323846
const val PI_F: Float = 3.14159265358979323846f
