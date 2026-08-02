package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.renderer.program.FillProgram

class FillProgramTest {

    @Test
    fun vertexPassesThroughMatrix() {
        val u = FillProgram.Uniforms(
            matrix = Matrix4.translate(100f, 50f).times(Matrix4.scale(2f)),
            color = Color.black(),
        )
        val out = FillProgram.vertex(10f, 20f, u)
        // (10,20) -> scale 2 -> (20,40) -> translate -> (120,90)
        assertEquals(120f, out.clip.x)
        assertEquals(90f, out.clip.y)
        assertEquals(1f, out.clip.w)
    }

    @Test
    fun fragmentAppliesOpacity() {
        val u = FillProgram.Uniforms(
            matrix = Matrix4.identity(),
            color = Color(1f, 0.5f, 0.25f, 1f),
            opacity = 0.5f,
        )
        val c = FillProgram.fragment(u)
        assertEquals(0.5f, c.r, 1e-5f)
        assertEquals(0.25f, c.g, 1e-5f)
        assertEquals(0.125f, c.b, 1e-5f)
        assertEquals(0.5f, c.a, 1e-5f)
    }

    @Test
    fun fullOpacityKeepsColor() {
        val u = FillProgram.Uniforms(
            matrix = Matrix4.identity(),
            color = Color.red(),
            opacity = 1f,
        )
        val c = FillProgram.fragment(u)
        assertEquals(1f, c.r, 1e-5f)
        assertEquals(1f, c.a, 1e-5f)
    }
}
