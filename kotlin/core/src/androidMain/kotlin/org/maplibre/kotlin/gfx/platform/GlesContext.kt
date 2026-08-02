package org.maplibre.kotlin.gfx.platform

import org.maplibre.kotlin.gfx.ColorMode
import org.maplibre.kotlin.gfx.CommandEncoder
import org.maplibre.kotlin.gfx.Context
import org.maplibre.kotlin.gfx.ContextObserver
import org.maplibre.kotlin.gfx.DepthMode
import org.maplibre.kotlin.gfx.Drawable
import org.maplibre.kotlin.gfx.RenderPass
import org.maplibre.kotlin.gfx.StencilMode
import android.opengl.GLES30

/**
 * OpenGL ES 3.0 graphics context for Android.
 * Ported from mbgl::gl::Context (structure subset).
 */
class GlesContext(
    private val observer: ContextObserver? = null,
) : Context {

    override fun beginFrame() {
        // nothing to do for GLES: the surface is already current
    }

    override fun endFrame() {
        GLES30.glFlush()
    }

    override fun performCleanup() {
        // nothing pending yet
    }

    override fun reduceMemoryUsage() {
        // nothing pending yet
    }

    override fun createCommandEncoder(): CommandEncoder = GlesCommandEncoder(this)

    override fun clearStencilBuffer(value: Int) {
        GLES30.glClearStencil(value)
        GLES30.glClear(GLES30.GL_STENCIL_BUFFER_BIT)
    }

    override fun setDirtyState() {
        // placeholder
    }

    override fun visualizeDepthBuffer(depthRangeSize: Float) {
        // placeholder
    }

    override fun visualizeStencilBuffer() {
        // placeholder
    }
}

/** GLES command encoder. */
class GlesCommandEncoder(private val context: Context) : CommandEncoder {
    override fun createRenderPass(
        colorMode: ColorMode,
        depthMode: DepthMode,
        stencilMode: StencilMode,
    ): RenderPass = GlesRenderPass(context, colorMode, depthMode, stencilMode)

    override fun endEncoding() {
        // placeholder
    }
}

/** GLES render pass: binds state and draws drawables. */
class GlesRenderPass(
    private val context: Context,
    private val colorMode: ColorMode,
    private val depthMode: DepthMode,
    private val stencilMode: StencilMode,
) : RenderPass {

    override fun draw(drawable: Drawable) {
        if (!drawable.isEnabled) return
        applyBlendState()
        applyDepthState()
        drawable.draw(org.maplibre.kotlin.gfx.PaintParameters(context, org.maplibre.kotlin.gfx.Frame(0, 0, 1.0f)))
    }

    private fun applyBlendState() {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(glesBlendEquation(colorMode.equation))
        GLES30.glBlendFunc(glesBlendFactor(colorMode.srcFactor), glesBlendFactor(colorMode.dstFactor))
    }

    private fun applyDepthState() {
        if (depthMode.mask == org.maplibre.kotlin.gfx.DepthMaskType.ReadWrite) {
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
        } else {
            GLES30.glDepthMask(false)
        }
    }

    private fun glesBlendEquation(eq: org.maplibre.kotlin.gfx.ColorBlendEquationType): Int = when (eq) {
        org.maplibre.kotlin.gfx.ColorBlendEquationType.Add -> GLES30.GL_FUNC_ADD
        org.maplibre.kotlin.gfx.ColorBlendEquationType.Subtract -> GLES30.GL_FUNC_SUBTRACT
        org.maplibre.kotlin.gfx.ColorBlendEquationType.ReverseSubtract -> GLES30.GL_FUNC_REVERSE_SUBTRACT
    }

    private fun glesBlendFactor(f: org.maplibre.kotlin.gfx.ColorBlendFactorType): Int = when (f) {
        org.maplibre.kotlin.gfx.ColorBlendFactorType.Zero -> GLES30.GL_ZERO
        org.maplibre.kotlin.gfx.ColorBlendFactorType.One -> GLES30.GL_ONE
        org.maplibre.kotlin.gfx.ColorBlendFactorType.SrcColor -> GLES30.GL_SRC_COLOR
        org.maplibre.kotlin.gfx.ColorBlendFactorType.OneMinusSrcColor -> GLES30.GL_ONE_MINUS_SRC_COLOR
        org.maplibre.kotlin.gfx.ColorBlendFactorType.SrcAlpha -> GLES30.GL_SRC_ALPHA
        org.maplibre.kotlin.gfx.ColorBlendFactorType.OneMinusSrcAlpha -> GLES30.GL_ONE_MINUS_SRC_ALPHA
        org.maplibre.kotlin.gfx.ColorBlendFactorType.DstAlpha -> GLES30.GL_DST_ALPHA
        org.maplibre.kotlin.gfx.ColorBlendFactorType.OneMinusDstAlpha -> GLES30.GL_ONE_MINUS_DST_ALPHA
        org.maplibre.kotlin.gfx.ColorBlendFactorType.DstColor -> GLES30.GL_DST_COLOR
        org.maplibre.kotlin.gfx.ColorBlendFactorType.OneMinusDstColor -> GLES30.GL_ONE_MINUS_DST_COLOR
        org.maplibre.kotlin.gfx.ColorBlendFactorType.SrcAlphaSaturate -> GLES30.GL_SRC_ALPHA_SATURATE
        org.maplibre.kotlin.gfx.ColorBlendFactorType.ConstantColor -> GLES30.GL_CONSTANT_COLOR
        org.maplibre.kotlin.gfx.ColorBlendFactorType.OneMinusConstantColor -> GLES30.GL_ONE_MINUS_CONSTANT_COLOR
        org.maplibre.kotlin.gfx.ColorBlendFactorType.ConstantAlpha -> GLES30.GL_CONSTANT_ALPHA
        org.maplibre.kotlin.gfx.ColorBlendFactorType.OneMinusConstantAlpha -> GLES30.GL_ONE_MINUS_CONSTANT_ALPHA
    }
}
