package org.maplibre.kotlin.gfx.platform

import org.maplibre.kotlin.gfx.ColorMode
import org.maplibre.kotlin.gfx.CommandEncoder
import org.maplibre.kotlin.gfx.Context
import org.maplibre.kotlin.gfx.ContextObserver
import org.maplibre.kotlin.gfx.DepthMode
import org.maplibre.kotlin.gfx.Drawable
import org.maplibre.kotlin.gfx.RenderPass
import org.maplibre.kotlin.gfx.StencilMode

/**
 * Metal graphics context for iOS.
 * Ported from mbgl::mtl::Context (structure subset).
 *
 * The actual MTLDevice/MTLCommandQueue wiring is provided by the host
 * application via [attach]; the render-pass bookkeeping mirrors the
 * mbgl Metal backend.
 */
class MetalContext(
    private val observer: ContextObserver? = null,
) : Context {

    /** Host-provided Metal device and command queue handles (raw pointers). */
    var deviceHandle: Long = 0
    var commandQueueHandle: Long = 0

    override fun beginFrame() {
        // Metal command buffer is created by the host's render loop
    }

    override fun endFrame() {
        // host commits the command buffer
    }

    override fun performCleanup() {
        // placeholder
    }

    override fun reduceMemoryUsage() {
        // placeholder
    }

    override fun createCommandEncoder(): CommandEncoder = MetalCommandEncoder(this)

    override fun clearStencilBuffer(value: Int) {
        // placeholder
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

/** Metal command encoder. */
class MetalCommandEncoder(private val context: Context) : CommandEncoder {
    override fun createRenderPass(
        colorMode: ColorMode,
        depthMode: DepthMode,
        stencilMode: StencilMode,
    ): RenderPass = MetalRenderPass(context, colorMode, depthMode, stencilMode)

    override fun endEncoding() {
        // placeholder
    }
}

/** Metal render pass. */
class MetalRenderPass(
    private val context: Context,
    private val colorMode: ColorMode,
    private val depthMode: DepthMode,
    private val stencilMode: StencilMode,
) : RenderPass {

    override fun draw(drawable: Drawable) {
        if (!drawable.isEnabled) return
        drawable.draw(org.maplibre.kotlin.gfx.PaintParameters(context, org.maplibre.kotlin.gfx.Frame(0, 0, 1.0f)))
    }
}
