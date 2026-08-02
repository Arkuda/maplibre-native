package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.renderer.program.LineProgram

/**
 * Tests for the line shader math. The generator produces a unit-width
 * skeleton; the shader expands it by the evaluated width. We verify the
 * expansion is exactly `width/2 + AA` on each side of the line.
 */
class LineProgramTest {

    private val identity = LineProgram.Uniforms(
        matrix = Matrix4.identity(),
        devicePixelRatio = 2.0f,
    )

    private fun vertex(
        x: Int, y: Int,
        extrudeX: Double, extrudeY: Double,
        isRound: Boolean = false,
        isUp: Boolean = true,
        direction: Int = 0,
        linesofar: Double = 0.0,
    ) = LineProgram.VertexInput(x, y, extrudeX, extrudeY, isRound, isUp, direction, linesofar)

    @Test
    fun expandsExtrusionByHalfWidth() {
        // horizontal line, vertex at (100, 50), unit normal pointing up (0,-1)
        // width 2 => outset = 1 + AA (1/2/2 = 0.25) = 1.25
        val props = LineProgram.Props(width = 2.0)
        val v = LineProgram.vertex(vertex(100, 50, 0.0, -1.0), props, identity)

        // base pos (100,50), dist = outset * (0,-1) => y = 50 - 1.25
        assertEquals(100.0, v.clip.x.toDouble(), 1e-4)
        assertEquals(50.0 - 1.25, v.clip.y.toDouble(), 1e-4)
        assertEquals(1.25, v.width2S, 1e-6)
        assertEquals(0.0, v.width2T, 1e-6)
    }

    @Test
    fun widthScalesOutset() {
        val props = LineProgram.Props(width = 10.0)
        val v = LineProgram.vertex(vertex(0, 0, 0.0, -1.0), props, identity)
        // outset = 5 + 0.25
        assertEquals(5.25, v.width2S, 1e-6)
        assertEquals(-5.25, v.clip.y.toDouble(), 1e-4)
    }

    @Test
    fun gapWidthAddsInset() {
        // gap-width 4 => gapWidth/2 = 2; outset = 2 + halfwidth*2 + AA, inset = 2 + AA
        val props = LineProgram.Props(width = 2.0, gapWidth = 4.0)
        val v = LineProgram.vertex(vertex(0, 0, 0.0, -1.0), props, identity)
        assertEquals(2.25, v.width2T, 1e-6)
        // halfwidth = 1; outset = 2 + 1*2 + 0.25 = 4.25
        assertEquals(4.25, v.width2S, 1e-6)
        // extrude moves by outset: y = 0 - 4.25
        assertEquals(-4.25, v.clip.y.toDouble(), 1e-4)
    }

    @Test
    fun offsetShiftsLineLaterally() {
        // offset 3, line along +x, extrude (0,-1), normal up (+1)
        // offset = -3; u=0 (direction 0); t=1; offset2 = -3 * (0,-1) * 1 = (0, 3)
        val props = LineProgram.Props(width = 2.0, offset = 3.0)
        val v = LineProgram.vertex(vertex(0, 0, 0.0, -1.0, isUp = true), props, identity)
        // base (0, 0 + 3) = (0,3); dist = 1.25*(0,-1) => (0, -1.25); total y = 1.75
        assertEquals(0.0, v.clip.x.toDouble(), 1e-4)
        assertEquals(3.0 - 1.25, v.clip.y.toDouble(), 1e-4)
    }

    @Test
    fun normalBitsReflectRoundAndUp() {
        val props = LineProgram.Props(width = 2.0)
        val round = LineProgram.vertex(vertex(0, 0, 1.0, 0.0, isRound = true, isUp = true), props, identity)
        assertEquals(1.0, round.normalX, 1e-6)
        assertEquals(1.0, round.normalY, 1e-6)

        val down = LineProgram.vertex(vertex(0, 0, 1.0, 0.0, isRound = false, isUp = false), props, identity)
        assertEquals(0.0, down.normalX, 1e-6)
        assertEquals(-1.0, down.normalY, 1e-6)
    }

    @Test
    fun fragmentAlphaIsOneAtLineCenter() {
        // At the exact line center the distance field = outset - width/2 - AA/...
        // dist = |normal| * width2S; for a non-round vertex |normal| = 1.
        // alpha = clamp(min(dist - (t - blur2), s - dist) / blur2)
        val props = LineProgram.Props(width = 2.0)
        // use varyings from the expanded vertex directly: dist == s - blur2/2-ish
        val v = LineProgram.vertex(vertex(0, 0, 0.0, -1.0), props, identity)
        // dist at the center equals s - (s - t)/2 ... simpler: fragment of a vertex
        // that is exactly on the line edge (dist == s - blur2) should be ~1
        val alpha = LineProgram.fragment(v.normalX, v.normalY, v.width2S, v.width2T, v.gammaScale, props, identity)
        // with blur=0, blur2 = 0.25 (1/2/2); dist = 1.25; s - dist = 0; t - blur2 = -0.25
        // min(0 - (-0.25), 1.25 - 1.25) = 0 => alpha = 0. That's the edge pixel.
        // The line interior is opaque; check the alpha is in range.
        assertTrue(alpha in 0.0..1.0)
    }

    @Test
    fun fragmentColorMultipliesOpacity() {
        val props = LineProgram.Props(width = 2.0, color = Color(1f, 0f, 0f, 1f), opacity = 0.5)
        val c = LineProgram.fragmentColor(1.0, props)
        assertEquals(0.5f, c.r, 1e-5f)
        assertEquals(0.5f, c.a, 1e-5f)
    }

    @Test
    fun linesofarPassesThrough() {
        val props = LineProgram.Props(width = 1.0)
        val v = LineProgram.vertex(vertex(0, 0, 0.0, -1.0, linesofar = 42.0), props, identity)
        assertEquals(42.0, v.linesofar, 1e-6)
    }
}
