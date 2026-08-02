package org.maplibre.kotlin.gfx

/**
 * JVM (host-testing) fallback context: no real GPU, used only to compile and
 * test the common logic on the host.
 */
class JvmContext : Context {
    override fun beginFrame() {}
    override fun endFrame() {}
    override fun performCleanup() {}
    override fun reduceMemoryUsage() {}
    override fun createCommandEncoder(): CommandEncoder = object : CommandEncoder {
        override fun createRenderPass(colorMode: ColorMode, depthMode: DepthMode, stencilMode: StencilMode): RenderPass =
            object : RenderPass {
                override fun draw(drawable: Drawable) {
                    if (drawable.isEnabled) {
                        drawable.draw(PaintParameters(this@JvmContext, Frame(0, 0, 1.0f)))
                    }
                }
            }

        override fun endEncoding() {}
    }

    override fun clearStencilBuffer(value: Int) {}
    override fun setDirtyState() {}
    override fun visualizeDepthBuffer(depthRangeSize: Float) {}
    override fun visualizeStencilBuffer() {}
}

actual fun createPlatformContext(): Context = JvmContext()

actual val backendName: String get() = "jvm-host"
