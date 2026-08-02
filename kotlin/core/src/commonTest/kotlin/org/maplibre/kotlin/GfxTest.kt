package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.gfx.ColorMode
import org.maplibre.kotlin.gfx.DepthMode
import org.maplibre.kotlin.gfx.createPlatformContext
import org.maplibre.kotlin.gfx.backendName

class GfxTest {

    @Test
    fun colorParseHex() {
        val c = Color.parse("#ff8000")!!
        assertEquals(1.0f, c.r)
        assertEquals(128 / 255f, c.g, 1e-6f)
        assertEquals(0.0f, c.b)
        assertEquals(1.0f, c.a)
    }

    @Test
    fun colorParseShortHex() {
        val c = Color.parse("#f80")!!
        assertEquals(1.0f, c.r)
        assertEquals(8 / 15f, c.g, 1e-6f)
        assertEquals(0.0f, c.b)
    }

    @Test
    fun colorParseWithAlpha() {
        val c = Color.parse("#ff800080")!!
        assertEquals(128 / 255f, c.a, 1e-6f)
    }

    @Test
    fun colorParseNamed() {
        assertEquals(Color.red(), Color.parse("red"))
        assertEquals(Color.white(), Color.parse("white"))
        assertNull(Color.parse("notacolor"))
    }

    @Test
    fun colorChannelsValidated() {
        // channels must be in [0,1]
        try {
            Color(r = 2.0f)
            throw AssertionError("should have thrown")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun colorModeDefaults() {
        assertEquals(org.maplibre.kotlin.gfx.ColorBlendFactorType.One, ColorMode.Replace.srcFactor)
        assertEquals(org.maplibre.kotlin.gfx.ColorBlendFactorType.OneMinusSrcAlpha, ColorMode.AlphaBlended.dstFactor)
        assertEquals(org.maplibre.kotlin.gfx.DepthMaskType.ReadOnly, DepthMode.Disabled.mask)
    }

    @Test
    fun platformContextCreates() {
        val ctx = createPlatformContext()
        assertNotNull(ctx)
        val encoder = ctx.createCommandEncoder()
        assertNotNull(encoder.createRenderPass(ColorMode.Replace, DepthMode.Disabled, org.maplibre.kotlin.gfx.StencilMode.Disabled))
        encoder.endEncoding()
        ctx.endFrame()
        // backend name should be non-empty
        assert(backendName.isNotEmpty())
    }
}
