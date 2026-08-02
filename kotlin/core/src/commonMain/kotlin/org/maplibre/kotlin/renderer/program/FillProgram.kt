package org.maplibre.kotlin.renderer.program

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.math.Vec4

/**
 * Fill shader — port of `shaders/fill.vertex.glsl` + `fill.fragment.glsl`.
 *
 * The fill program is trivial: vertex positions pass through the matrix
 * unchanged, and the fragment color is `color * opacity`. Everything else in
 * the original shader (outline, pattern) is a separate program.
 */
object FillProgram {

    /** Per-draw uniforms. Mirrors FillDrawableUBO + FillEvaluatedPropsUBO. */
    class Uniforms(
        val matrix: Matrix4,
        val color: Color,
        val opacity: Float = 1.0f,
    )

    /** Output of the vertex stage: clip-space position. */
    class VertexOutput(val clip: Vec4)

    /**
     * Vertex stage: `gl_Position = u_matrix * vec4(a_pos, 0, 1)`.
     *
     * @param x tile-unit x of the vertex
     * @param y tile-unit y of the vertex
     */
    fun vertex(x: Float, y: Float, uniforms: Uniforms): VertexOutput {
        val clip = uniforms.matrix.times(Vec4(x, y, 0.0f, 1.0f))
        return VertexOutput(clip)
    }

    /**
     * Fragment stage: `fragColor = color * opacity`.
     * Returns the premultiplied color.
     */
    fun fragment(uniforms: Uniforms): Color {
        val a = uniforms.opacity.coerceIn(0.0f, 1.0f)
        return Color(
            uniforms.color.r * a,
            uniforms.color.g * a,
            uniforms.color.b * a,
            uniforms.color.a * a,
        )
    }
}
