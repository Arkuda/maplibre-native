package org.maplibre.kotlin.math

/**
 * Computes the ceiling of the base-2 logarithm of x, i.e. the smallest
 * integer n such that 2^n >= x.
 */
fun ceilLog2(x: Long): Int {
    // 0xFFFFFFFF00000000 as a signed 64-bit two's complement value
    val t = longArrayOf(
        -0x100000000L,
        0x00000000FFFF0000L,
        0x000000000000FF00L,
        0x00000000000000F0L,
        0x000000000000000CL,
        0x0000000000000002L,
    )
    var y = if ((x and (x - 1)) == 0L) 0 else 1
    var j = 32
    var xx = x
    for (i in t) {
        val k = if ((xx and i) == 0L) 0 else j
        y += k
        xx = xx shr k
        j = j shr 1
    }
    return y
}
