package org.maplibre.kotlin.renderer.program

import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.math.Vec4

/**
 * Symbol shader — port of shaders/symbol_sdf.vertex.glsl + fragment.glsl,
 * reduced to what the software rasterizer needs. The real symbol pipeline
 * uploads glyphs into an SDF atlas and renders quads; here the projection of
 * the anchor point is all the vertex stage needs, and text is drawn from the
 * embedded [org.maplibre.kotlin.util.BitmapFont] directly in screen space.
 */
object SymbolProgram {

    class Uniforms(
        val matrix: Matrix4,
        val devicePixelRatio: Float,
    )

    class VertexOutput(val clip: Vec4)

    fun vertex(
        x: Float,
        y: Float,
        uniforms: Uniforms,
    ): VertexOutput = VertexOutput(uniforms.matrix.times(Vec4(x, y, 0f, 1f)))
}
