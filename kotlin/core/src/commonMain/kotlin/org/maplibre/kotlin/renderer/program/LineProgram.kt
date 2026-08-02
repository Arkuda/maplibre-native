package org.maplibre.kotlin.renderer.program

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.math.Vec4
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Line shader — port of `shaders/line.vertex.glsl` + `line.fragment.glsl`.
 *
 * This is the heart of line rendering: the bucket stores a *unit-width
 * skeleton* (position + unit extrusion + join/cap flags), and this program
 * expands the extrusion by the evaluated line width, applies the offset and
 * gap width, and computes the antialiasing factors for the fragment stage.
 *
 * The original GLSL packs positions/extrusions into bytes and unpacks them in
 * the shader. Here the geometry is already unpacked (typed vertex struct), so
 * the math below is exactly the shader math without the bit-twiddling.
 */
object LineProgram {

    /** Line layout/paint properties, evaluated for the current zoom/feature. */
    class Props(
        val width: Double = 1.0,
        val gapWidth: Double = 0.0,
        val offset: Double = 0.0,
        val blur: Double = 0.0,
        val color: Color = Color.black(),
        val opacity: Double = 1.0,
    )

    /** Per-draw uniforms. Mirrors LineDrawableUBO + GlobalPaintParamsUBO. */
    class Uniforms(
        val matrix: Matrix4,
        /** Tile size to pixel ratio (EXTENT / pixel width of the tile). */
        val ratio: Float = 1.0f,
        /** Device pixel ratio of the screen (used for antialiasing). */
        val devicePixelRatio: Float = 2.0f,
        /** Units-to-pixels for gamma scale: world size in pixels / tile size. */
        val unitsToPixels: Float = 1.0f,
    )

    /** Input vertex: the typed equivalent of a_pos_normal + a_data. */
    class VertexInput(
        val x: Int,
        val y: Int,
        /** Unit extrusion (already unpacked from a_data.xy - 128). */
        val extrudeX: Double,
        val extrudeY: Double,
        /** True for round caps/joins (a_pos_normal round bit). */
        val isRound: Boolean,
        /** True if the normal points "up" (a_pos_normal up bit). */
        val isUp: Boolean,
        /** -1/0/1 line direction (mod(a_data.z, 4) - 1). */
        val direction: Int,
        /** Distance along the line in tile units (a_data.z/w). */
        val linesofar: Double,
    )

    /** Output of the vertex stage (varyings + clip position). */
    class VertexOutput(
        val clip: Vec4,
        val normalX: Double,
        val normalY: Double,
        val width2S: Double,
        val width2T: Double,
        val gammaScale: Double,
        val linesofar: Double,
    )

    /**
     * Vertex stage — exact port of `line.vertex.glsl`.
     */
    fun vertex(input: VertexInput, props: Props, uniforms: Uniforms): VertexOutput {
        // the distance over which the line edge fades out.
        // Retina devices need a smaller distance to avoid aliasing.
        val antialiasing = 1.0 / uniforms.devicePixelRatio / 2.0

        // a_extrude and a_direction arrive unpacked in our vertex struct.
        val extrudeX = input.extrudeX
        val extrudeY = input.extrudeY
        val aDirection = input.direction.toDouble()

        val linesofar = input.linesofar

        val posX = input.x.toDouble()
        val posY = input.y.toDouble()

        // x is 1 if it's a round cap, 0 otherwise
        // y is 1 if the normal points up, and -1 if it points down
        val normalX = if (input.isRound) 1.0 else 0.0
        val normalY = if (input.isUp) 1.0 else -1.0

        // these transformations used to be applied in the JS and native code bases.
        // moved them into the shader for clarity and simplicity.
        val gapWidth = props.gapWidth / 2.0
        val halfWidth = props.width / 2.0
        val offset = -1.0 * props.offset

        val inset = gapWidth + (if (gapWidth > 0.0) antialiasing else 0.0)
        val outset = gapWidth + halfWidth * (if (gapWidth > 0.0) 2.0 else 1.0) +
            (if (halfWidth == 0.0) 0.0 else antialiasing)

        // Scale the extrusion vector down to a normal and then up by the line
        // width of this vertex.
        val distX = outset * extrudeX
        val distY = outset * extrudeY
        // Calculate the offset when drawing a line that is to the side of the
        // actual line. We do this by creating a vector that points towards the
        // extrude, but rotate it when we're drawing round end points since
        // their extrude vector points in another direction.
        val u = 0.5 * aDirection
        val t = 1.0 - abs(u)
        // mat2(t, -u, u, t) * (extrude * normal.y)
        val rotX = t * extrudeX + u * extrudeY
        val rotY = -u * extrudeX + t * extrudeY
        val offsetX = offset * rotX * normalY
        val offsetY = offset * rotY * normalY

        val ratio = uniforms.ratio.toDouble()

        // projected_extrude = u_matrix * vec4(dist / u_ratio, 0, 0)
        val projExtrude = uniforms.matrix.times(
            Vec4((distX / ratio).toFloat(), (distY / ratio).toFloat(), 0.0f, 0.0f),
        )

        // gl_Position = u_matrix * vec4(pos + offset2 / u_ratio, 0, 1) + projected_extrude
        val base = uniforms.matrix.times(
            Vec4(
                ((posX + offsetX / ratio).toFloat()),
                ((posY + offsetY / ratio).toFloat()),
                0.0f,
                1.0f,
            ),
        )
        val clip = Vec4(
            base.x + projExtrude.x,
            base.y + projExtrude.y,
            base.z + projExtrude.z,
            base.w + projExtrude.w,
        )

        // calculate how much the perspective view squishes or stretches the extrude
        val extrudeLengthWithoutPerspective = sqrt(distX * distX + distY * distY)
        val projectedX = projExtrude.x / clip.w * uniforms.unitsToPixels
        val projectedY = projExtrude.y / clip.w * uniforms.unitsToPixels
        val extrudeLengthWithPerspective = sqrt(projectedX * projectedX + projectedY * projectedY)
        val gammaScale = if (extrudeLengthWithPerspective == 0.0f) 1.0
        else extrudeLengthWithoutPerspective / extrudeLengthWithPerspective.toDouble()

        return VertexOutput(
            clip = clip,
            normalX = normalX,
            normalY = normalY,
            width2S = outset,
            width2T = inset,
            gammaScale = gammaScale,
            linesofar = linesofar,
        )
    }

    /**
     * Fragment stage — exact port of `line.fragment.glsl`.
     *
     * @param vNormal the varying normal (round bit + up bit) from [VertexOutput]
     * @param width2S `v_width2.s` = outset (outer half-width + AA)
     * @param width2T `v_width2.t` = inset (gap half-width + AA)
     * @param gammaScale `v_gamma_scale` from [VertexOutput]
     * @return the alpha of this fragment in [0, 1]
     */
    fun fragment(
        vNormalX: Double,
        vNormalY: Double,
        width2S: Double,
        width2T: Double,
        gammaScale: Double,
        props: Props,
        uniforms: Uniforms,
    ): Double {
        // Calculate the distance of the pixel from the line in pixels.
        val dist = sqrt(vNormalX * vNormalX + vNormalY * vNormalY) * width2S

        // Calculate the antialiasing fade factor. This is either when fading in
        // the line in case of an offset line (v_width2.t) or when fading out
        // (v_width2.s)
        val blur2 = (props.blur + 1.0 / uniforms.devicePixelRatio) * gammaScale
        val alpha = ((minOf(dist - (width2T - blur2), width2S - dist)) / blur2).coerceIn(0.0, 1.0)
        return alpha
    }

    /** Convenience: fragment color = color * (alpha * opacity). */
    fun fragmentColor(alpha: Double, props: Props): Color {
        val a = (alpha * props.opacity).coerceIn(0.0, 1.0)
        return Color(
            (props.color.r * a).toFloat(),
            (props.color.g * a).toFloat(),
            (props.color.b * a).toFloat(),
            (props.color.a * a).toFloat(),
        )
    }
}
