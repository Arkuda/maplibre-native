package org.maplibre.kotlin.gfx

/**
 * A drawable: a renderable unit with geometry, shader and state.
 * Ported from mbgl::gfx::Drawable (structure subset).
 */
interface Drawable {
    val id: String
    val shader: ShaderProgram?
    val colorMode: ColorMode
    val depthMode: DepthMode
    val stencilMode: StencilMode
    val cullFaceMode: CullFaceMode
    val isEnabled: Boolean

    fun draw(paintParameters: PaintParameters)
}

/** Shader program abstraction. */
interface ShaderProgram {
    val name: String
}

/** Parameters passed to draw(). Ported from mbgl::gfx::PaintParameters. */
class PaintParameters(
    val context: Context,
    val frame: Frame,
)

/** One rendered frame. */
class Frame(
    val width: Int,
    val height: Int,
    val pixelRatio: Float,
)

/** A render pass: the unit of work between begin/end of a frame. */
interface RenderPass {
    fun draw(drawable: Drawable)
}

/** Command encoder for a frame. Ported from mbgl::gfx::CommandEncoder. */
interface CommandEncoder {
    fun createRenderPass(colorMode: ColorMode, depthMode: DepthMode, stencilMode: StencilMode): RenderPass
    fun endEncoding()
}

/**
 * Graphics context: the platform-independent entry point into a backend
 * (OpenGL ES on Android, Metal on iOS).
 * Ported from mbgl::gfx::Context (structure subset).
 */
interface Context {
    fun beginFrame()
    fun endFrame()
    fun performCleanup()
    fun reduceMemoryUsage()

    /** Creates a command encoder for the current frame. */
    fun createCommandEncoder(): CommandEncoder

    fun clearStencilBuffer(value: Int)
    fun setDirtyState()

    /** Draw mode for the default (full-screen) pass. */
    fun visualizeDepthBuffer(depthRangeSize: Float)
    fun visualizeStencilBuffer()

    /** Creates a drawable builder. */
    fun createDrawableBuilder(name: String): DrawableBuilder
}

/**
 * Simple context observer. Ported from mbgl::gfx::ContextObserver.
 */
interface ContextObserver {
    fun onContextLost()
    fun onContextRestored()
    fun onContextDestroyed()
}
