package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.renderer.program.FillExtrusionProgram
import kotlin.math.abs

class FillExtrusionProgramTest {

    @Test
    fun defaultLightPositionCartesian() {
        // style default light: [1.15, 210, 30] (radial, azimuthal, polar)
        val p = FillExtrusionProgram.lightPosition(1.15, 210.0, 30.0, 0.0)
        // _a = deg2rad(210 + 90) = 300deg; _p = deg2rad(30)
        assertEquals(1.15 * 0.5 * 0.5, p[0].toDouble(), 1e-4) // x = r*cos(300)*sin(30)
        assertEquals(1.15 * -0.8660254 * 0.5, p[1].toDouble(), 1e-4) // y = r*sin(300)*sin(30)
        assertEquals(1.15 * 0.8660254, p[2].toDouble(), 1e-4) // z = r*cos(30)
    }

    @Test
    fun lightPositionRotatesWithBearing() {
        val b0 = FillExtrusionProgram.lightPosition(1.0, 0.0, 90.0, 0.0)
        // azimuth 0 + 90 = 90deg: x = cos(90)=0, y = sin(90)=1, z = cos(90)=0
        assertEquals(0.0, b0[0].toDouble(), 1e-6)
        assertEquals(1.0, b0[1].toDouble(), 1e-6)

        val b90 = FillExtrusionProgram.lightPosition(1.0, 0.0, 90.0, 90.0)
        // rotate by -90: (0,1) -> (1,0)
        assertEquals(1.0, b90[0].toDouble(), 1e-6)
        assertEquals(0.0, b90[1].toDouble(), 1e-6)
    }

    private fun uniforms(
        color: Color = Color.white(),
        opacity: Double = 1.0,
        verticalGradient: Boolean = false,
    ) = FillExtrusionProgram.Uniforms(
        matrix = Matrix4.identity(),
        lightPos = floatArrayOf(0.0f, 0.0f, 1.0f), // light straight up
        lightColor = floatArrayOf(1.0f, 1.0f, 1.0f),
        lightIntensity = 0.5,
        color = color,
        verticalGradient = verticalGradient,
        opacity = opacity,
    )

    private fun v(
        t: Double = 1.0,
        nx: Double = 0.0,
        ny: Double = 0.0,
        nz: Double = 1.0,
        base: Double = 0.0,
        height: Double = 100.0,
        u: FillExtrusionProgram.Uniforms = uniforms(),
    ) = FillExtrusionProgram.vertex(0.0, 0.0, t, nx, ny, nz, base, height, u)

    @Test
    fun roofFacingLightIsBright() {
        // roof normal (0,0,1) points straight at the light: directional = 1
        val v = v()
        // colorvalue = 1 (white), directional -> 1 -> clamp((1+0.03)*1) = 1
        assertEquals(1.0f, v.color.r, 1e-4f)
        assertEquals(1.0f, v.color.a, 1e-4f)
        // elevated to height
        assertEquals(100.0f, v.clip.z, 1e-4f)
    }

    @Test
    fun opacityScalesColor() {
        val v = v(u = uniforms(opacity = 0.8))
        assertEquals(0.8f, v.color.r, 1e-4f)
        assertEquals(0.8f, v.color.a, 1e-4f)
    }

    @Test
    fun wallAwayFromLightIsDark() {
        // wall normal (0,1,0) perpendicular-ish to light (0,0,1): dot = 0
        val v = v(ny = 1.0, nz = 0.0)
        assertTrue(v.color.r < 0.6f, "wall away from light must be darker than roof: ${v.color.r}")
        assertEquals(1.0f, v.color.a, 1e-4f)
    }

    @Test
    fun verticalGradientDarkensWallBase() {
        // wall with vertical gradient: t=0 (base) gets the clamp lower bound,
        // t=1 (top) gets a larger multiplier (needs height > 150 for contrast)
        val u = uniforms(verticalGradient = true)
        val base = v(t = 0.0, ny = 1.0, nz = 0.0, height = 300.0, u = u)
        val top = v(t = 1.0, ny = 1.0, nz = 0.0, height = 300.0, u = u)
        assertTrue(base.color.r < top.color.r, "base must be darker than top: ${base.color.r} vs ${top.color.r}")
    }

    @Test
    fun baseVertexProjectsToBaseHeight() {
        val v = v(t = 0.0, ny = 1.0, nz = 0.0, base = 5.0, height = 100.0)
        assertEquals(5.0f, v.clip.z, 1e-4f)
    }

    @Test
    fun clipXyUnchangedWithIdentity() {
        val out = FillExtrusionProgram.vertex(12.0, 34.0, 0.0, 0.0, 0.0, 1.0, 0.0, 100.0, uniforms())
        assertEquals(12.0f, out.clip.x, 1e-4f)
        assertEquals(34.0f, out.clip.y, 1e-4f)
        assertEquals(1.0f, out.clip.w, 1e-4f)
    }
}
