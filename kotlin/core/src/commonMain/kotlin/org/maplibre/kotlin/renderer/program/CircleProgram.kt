package org.maplibre.kotlin.renderer.program

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.math.Vec4

/**
 * Circle shader — port of shaders/circle.vertex.glsl + circle.fragment.glsl.
 *
 * The C++ vertex shader un-packs the extrusion from a_pos and expands the
 * quad in clip space:
 *   gl_Position = u_matrix * vec4(center, 0, 1);
 *   gl_Position.xy += extrude * (radius + stroke_width) * u_extrude_scale
 *                     * (scaleWithMap ? u_camera_to_center_distance : pos.w);
 * and passes v_data = (extrude.x, extrude.y, antialiasblur).
 *
 * Here the typed bucket stores position + extrusion separately; the vertex
 * step projects the center and the rasterizer expands the circle in screen
 * space using the evaluated radius (in tile units when scale-with-map).
 */
object CircleProgram {

    /** Uniforms shared by vertex + fragment stages. */
    class Uniforms(
        val matrix: Matrix4,
        /** Device pixel ratio (u_pixel_ratio / global pixel ratio). */
        val devicePixelRatio: Float,
    )

    /** Evaluated paint props (u_color/u_radius/u_blur/u_opacity/...). */
    class Props(
        val color: Color,
        val radius: Double,
        val blur: Double,
        val opacity: Double,
        val strokeColor: Color,
        val strokeWidth: Double,
        val strokeOpacity: Double,
        val scaleWithMap: Boolean,
    )

    /** Projected circle center. */
    class VertexOutput(val clip: Vec4)

    fun vertex(
        x: Float,
        y: Float,
        uniforms: Uniforms,
    ): VertexOutput = VertexOutput(uniforms.matrix.times(Vec4(x, y, 0f, 1f)))

    /**
     * Fragment shading — returns premultiplied RGBA for a pixel at the given
     * normalized distance from the circle center (0 = center, 1 = outer edge).
     * Ports the GLSL: opacity_t / color_t smoothsteps + mix().
     */
    fun fragment(
        /** Normalized distance, 0..1 = center..outer edge (radius + stroke). */
        d: Double,
        props: Props,
        uniforms: Uniforms,
    ): Color {
        val radiusOuter = props.radius + props.strokeWidth
        if (radiusOuter <= 0.0) return Color(0f, 0f, 0f, 0f)

        // antialiasblur = 1 / DPR / (radius + stroke_width)  (normalized)
        val antialiasBlur = 1.0 / uniforms.devicePixelRatio / radiusOuter
        val antialiasedBlur = -maxOf(props.blur, antialiasBlur)

        // opacity_t = smoothstep(0, antialiased_blur, d - 1)
        val opacityT = smoothstep(0.0, antialiasedBlur, d - 1.0)

        // color_t = stroke_width < 0.01 ? 0 : smoothstep(antialiased_blur, 0, d - radius/(radius+stroke))
        val colorT = if (props.strokeWidth < 0.01) {
            0.0
        } else {
            smoothstep(antialiasedBlur, 0.0, d - props.radius / radiusOuter)
        }

        val inner = Color(
            props.color.r * props.opacity.toFloat(),
            props.color.g * props.opacity.toFloat(),
            props.color.b * props.opacity.toFloat(),
            props.color.a * props.opacity.toFloat(),
        )
        val stroke = Color(
            props.strokeColor.r * props.strokeOpacity.toFloat(),
            props.strokeColor.g * props.strokeOpacity.toFloat(),
            props.strokeColor.b * props.strokeOpacity.toFloat(),
            props.strokeColor.a * props.strokeOpacity.toFloat(),
        )
        val mixed = Color(
            inner.r + (stroke.r - inner.r) * colorT.toFloat(),
            inner.g + (stroke.g - inner.g) * colorT.toFloat(),
            inner.b + (stroke.b - inner.b) * colorT.toFloat(),
            inner.a + (stroke.a - inner.a) * colorT.toFloat(),
        )
        // premultiplied, alpha applied
        val a = (mixed.a * opacityT).toFloat().coerceIn(0f, 1f)
        return Color(
            (mixed.r * opacityT).toFloat().coerceIn(0f, 1f),
            (mixed.g * opacityT).toFloat().coerceIn(0f, 1f),
            (mixed.b * opacityT).toFloat().coerceIn(0f, 1f),
            a,
        )
    }

    /** GLSL smoothstep: Hermite interpolation clamped to [0, 1]. */
    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}
