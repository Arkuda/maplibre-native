package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.geometry.Earcut

class EarcutTest {

    @Test
    fun squareTriangulates() {
        // unit square: 4 vertices, expect 2 triangles (6 indices)
        val data = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val tris = Earcut.triangulate(data)
        assertEquals(6, tris.size) // 2 triangles
        // total area must be 1.0
        assertEquals(1.0, triangleAreaSum(data, tris), 1e-9)
    }

    @Test
    fun pentagonTriangulates() {
        // regular pentagon => 3 triangles (9 indices), area matches
        val data = doubleArrayOf(
            0.0, 1.0,
            -0.9510565, 0.3090169,
            -0.5877852, -0.8090169,
            0.5877852, -0.8090169,
            0.9510565, 0.3090169,
        )
        val tris = Earcut.triangulate(data)
        assertEquals(9, tris.size) // 3 triangles
        // area of unit pentagon ≈ 2.3776
        assertEquals(2.3776, triangleAreaSum(data, tris), 1e-3)
    }

    @Test
    fun squareWithHole() {
        // outer square (0,0)-(10,10), inner square (2,2)-(8,8)
        val data = doubleArrayOf(
            0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0, // outer
            2.0, 2.0, 8.0, 2.0, 8.0, 8.0, 2.0, 8.0, // hole
        )
        val holeIndices = intArrayOf(4)
        val tris = Earcut.triangulate(data, holeIndices)
        // area = 100 - 36 = 64
        assertEquals(64.0, triangleAreaSum(data, tris), 1e-9)
    }

    @Test
    fun clockwiseAndCounterclockwiseBothWork() {
        // same square, opposite winding
        val cw = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0)
        val ccw = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val t1 = Earcut.triangulate(cw)
        val t2 = Earcut.triangulate(ccw)
        assertEquals(1.0, triangleAreaSum(cw, t1), 1e-9)
        assertEquals(1.0, triangleAreaSum(ccw, t2), 1e-9)
    }

    @Test
    fun concavePolygon() {
        // concave "L" shape: (0,0)-(3,0)-(3,1)-(1,1)-(1,3)-(0,3)
        // area = 3x1 bottom bar + 2x1 left bar = 5
        val data = doubleArrayOf(
            0.0, 0.0,
            3.0, 0.0,
            3.0, 1.0,
            1.0, 1.0,
            1.0, 3.0,
            0.0, 3.0,
        )
        val tris = Earcut.triangulate(data)
        assertEquals(5.0, triangleAreaSum(data, tris), 1e-9)
    }

    private fun triangleAreaSum(data: DoubleArray, tris: IntArray): Double {
        var sum = 0.0
        var i = 0
        while (i < tris.size) {
            val i0 = tris[i] * 2
            val i1 = tris[i + 1] * 2
            val i2 = tris[i + 2] * 2
            val x0 = data[i0]
            val y0 = data[i0 + 1]
            val x1 = data[i1]
            val y1 = data[i1 + 1]
            val x2 = data[i2]
            val y2 = data[i2 + 1]
            sum += kotlin.math.abs((x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)) / 2.0
            i += 3
        }
        return sum
    }
}
