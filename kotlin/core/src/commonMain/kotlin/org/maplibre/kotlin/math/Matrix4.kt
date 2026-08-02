package org.maplibre.kotlin.math

/**
 * Minimal 4x4 matrix (column-major, like GLSL/OpenGL) used by the shader
 * programs. Ported from mbgl::mat4 (subset needed by fill/line shaders).
 */
class Matrix4(
    /** 16 floats, column-major: m[col * 4 + row]. */
    val m: FloatArray = FloatArray(16),
) {

    operator fun get(col: Int, row: Int): Float = m[col * 4 + row]

    /** Matrix-vector multiplication: this * v. */
    fun times(v: Vec4): Vec4 = Vec4(
        m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12] * v.w,
        m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13] * v.w,
        m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14] * v.w,
        m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15] * v.w,
    )

    /** Matrix-matrix multiplication: this * other. */
    fun times(other: Matrix4): Matrix4 {
        val r = FloatArray(16)
        for (c in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0.0f
                for (k in 0 until 4) {
                    sum += m[k * 4 + row] * other.m[c * 4 + k]
                }
                r[c * 4 + row] = sum
            }
        }
        return Matrix4(r)
    }

    fun copy(): Matrix4 = Matrix4(m.copyOf())

    companion object {
        fun identity(): Matrix4 = Matrix4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
        )

        /** Uniform scale about the origin. */
        fun scale(s: Float): Matrix4 = Matrix4(
            floatArrayOf(
                s, 0f, 0f, 0f,
                0f, s, 0f, 0f,
                0f, 0f, s, 0f,
                0f, 0f, 0f, 1f,
            ),
        )

        /** Translation. */
        fun translate(x: Float, y: Float, z: Float = 0f): Matrix4 = Matrix4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, z, 1f,
            ),
        )
    }
}

/** 4D vector used by matrix math. */
data class Vec4(val x: Float, val y: Float, val z: Float, val w: Float)
