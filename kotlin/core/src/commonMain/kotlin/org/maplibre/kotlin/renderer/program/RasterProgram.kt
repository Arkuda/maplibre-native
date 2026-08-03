package org.maplibre.kotlin.renderer.program

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Raster shader — port of shaders/raster.fragment.glsl. The vertex stage is
 * the identity (a full-tile quad at tile-space corners); the fragment stage
 * applies hue spin, saturation, contrast and brightness to the sampled
 * texture color.
 */
object RasterProgram {

    /** Evaluated props converted to the UBO fields of the C++ shader. */
    class Props(
        val opacity: Float,
        /** u_spin_weights (xyz) — hue rotation matrix row. */
        val spinWeights: FloatArray,
        /** u_saturation_factor */
        val saturationFactor: Float,
        /** u_contrast_factor */
        val contrastFactor: Float,
        val brightnessLow: Float,
        val brightnessHigh: Float,
    )

    /** Port of the raster_layer_tweaker spinWeights() helper. */
    fun spinWeights(spinDeg: Double): FloatArray {
            val spin = spinDeg * PI / 180.0
            val s = sin(spin)
            val c = cos(spin)
            return floatArrayOf(
                ((2 * c + 1) / 3).toFloat(),
                ((-sqrt(3.0) * s - c + 1) / 3).toFloat(),
                ((sqrt(3.0) * s - c + 1) / 3).toFloat(),
            )
        }

        /** Port of the tweaker's saturationFactor() helper. */
        fun saturationFactor(saturation: Double): Float =
            if (saturation > 0) {
                (1.0 - 1.0 / (1.001 - saturation)).toFloat()
            } else {
                (-saturation).toFloat()
            }

        /** Port of the tweaker's contrastFactor() helper. */
        fun contrastFactor(contrast: Double): Float =
            if (contrast > 0) {
                (1.0 / (1.0 - contrast)).toFloat()
            } else {
                (1.0 + contrast).toFloat()
            }

        /**
         * Applies the full fragment chain to one RGBA texel.
         * Mirrors raster.fragment.glsl main(): un-premultiply, opacity,
         * spin (u_spin_weights), saturation, contrast, brightness.
         * @return adjusted RGBA, premultiplied by alpha
         */
        fun shade(r: Float, g: Float, b: Float, a: Float, props: Props): FloatArray {
            var cr = r
            var cg = g
            var cb = b
            var ca = a
            // un-premultiply (textures are premultiplied in GL; ours are not,
            // but keep the same guards as GLSL)
            if (ca > 0f) {
                cr /= ca
                cg /= ca
                cb /= ca
            }
            ca *= props.opacity

            // spin (hue rotation)
            val sw = props.spinWeights
            val sr = cr * sw[0] + cg * sw[1] + cb * sw[2]
            val sg = cr * sw[2] + cg * sw[0] + cb * sw[1]
            val sb = cr * sw[1] + cg * sw[2] + cb * sw[0]
            cr = sr; cg = sg; cb = sb

            // saturation
            val average = (cr + cg + cb) / 3f
            cr += (average - cr) * props.saturationFactor
            cg += (average - cg) * props.saturationFactor
            cb += (average - cb) * props.saturationFactor

            // contrast
            cr = (cr - 0.5f) * props.contrastFactor + 0.5f
            cg = (cg - 0.5f) * props.contrastFactor + 0.5f
            cb = (cb - 0.5f) * props.contrastFactor + 0.5f

            // brightness
            cr = props.brightnessLow + (props.brightnessHigh - props.brightnessLow) * cr
            cg = props.brightnessLow + (props.brightnessHigh - props.brightnessLow) * cg
            cb = props.brightnessLow + (props.brightnessHigh - props.brightnessLow) * cb

            return floatArrayOf(cr * ca, cg * ca, cb * ca, ca)
        }
}
