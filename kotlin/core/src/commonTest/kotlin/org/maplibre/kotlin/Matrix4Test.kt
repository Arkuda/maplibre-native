package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.math.Vec4

class Matrix4Test {

    @Test
    fun identityLeavesVectorUnchanged() {
        val v = Matrix4.identity().times(Vec4(3f, -2f, 5f, 1f))
        assertEquals(3f, v.x)
        assertEquals(-2f, v.y)
        assertEquals(5f, v.z)
        assertEquals(1f, v.w)
    }

    @Test
    fun scaleScalesComponents() {
        val v = Matrix4.scale(2f).times(Vec4(1f, 3f, -1f, 1f))
        assertEquals(2f, v.x)
        assertEquals(6f, v.y)
        assertEquals(-2f, v.z)
        assertEquals(1f, v.w)
    }

    @Test
    fun translateMovesPoint() {
        val v = Matrix4.translate(10f, 20f).times(Vec4(1f, 1f, 0f, 1f))
        assertEquals(11f, v.x)
        assertEquals(21f, v.y)
    }

    @Test
    fun multiplicationComposes() {
        // translate(5,0) * scale(2): point (1,1) -> (7,2)
        val m = Matrix4.translate(5f, 0f).times(Matrix4.scale(2f))
        val v = m.times(Vec4(1f, 1f, 0f, 1f))
        assertEquals(7f, v.x)
        assertEquals(2f, v.y)
    }
}
