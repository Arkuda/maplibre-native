package org.maplibre.kotlin.gfx

/**
 * A premultiplied color, all channels 0..1.
 * Ported from mbgl::Color.
 */
data class Color(
    val r: Float = 0.0f,
    val g: Float = 0.0f,
    val b: Float = 0.0f,
    val a: Float = 0.0f,
) {
    init {
        require(r in 0.0f..1.0f && g in 0.0f..1.0f && b in 0.0f..1.0f && a in 0.0f..1.0f) {
            "Color channels must be in [0, 1]"
        }
    }

    fun toArray(): FloatArray = floatArrayOf(r, g, b, a)

    fun toArrayDouble(): DoubleArray = doubleArrayOf(r.toDouble(), g.toDouble(), b.toDouble(), a.toDouble())

    companion object {
        fun black() = Color(0.0f, 0.0f, 0.0f, 1.0f)
        fun white() = Color(1.0f, 1.0f, 1.0f, 1.0f)
        fun red() = Color(1.0f, 0.0f, 0.0f, 1.0f)
        fun green() = Color(0.0f, 1.0f, 0.0f, 1.0f)
        fun blue() = Color(0.0f, 0.0f, 1.0f, 1.0f)
        fun transparent() = Color(0.0f, 0.0f, 0.0f, 0.0f)

        /** Parses CSS hex, HSL(A), or a small set of named colors. */
        fun parse(s: String): Color? {
            val value = s.trim()
            // named colors take precedence (e.g. "red" is not a valid hex digit string)
            val named = when (value.lowercase()) {
                "red" -> red()
                "green" -> green()
                "blue" -> blue()
                "black" -> black()
                "white" -> white()
                "transparent" -> transparent()
                else -> null
            }
            if (named != null) return named
            parseHsl(value)?.let { return it }

            val hex = value.removePrefix("#")
            if (hex.length == 6 || hex.length == 8) {
                val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                val a = if (hex.length == 8) (hex.substring(6, 8).toIntOrNull(16) ?: 255) else 255
                return Color(r / 255f, g / 255f, b / 255f, a / 255f)
            }
            if (hex.length == 3) {
                val r = hex[0].digitToIntOrNull(16) ?: return null
                val g = hex[1].digitToIntOrNull(16) ?: return null
                val b = hex[2].digitToIntOrNull(16) ?: return null
                return Color(r / 15f, g / 15f, b / 15f, 1.0f)
            }
            return null
        }

        private fun parseHsl(value: String): Color? {
            val match = Regex(
                """hsla?\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)%\s*,\s*([-+]?\d*\.?\d+)%\s*(?:,\s*([-+]?\d*\.?\d+)\s*)?\)""",
                RegexOption.IGNORE_CASE,
            ).matchEntire(value) ?: return null
            val hue = match.groupValues[1].toFloatOrNull() ?: return null
            val saturation = match.groupValues[2].toFloatOrNull()?.div(100f) ?: return null
            val lightness = match.groupValues[3].toFloatOrNull()?.div(100f) ?: return null
            val alpha = match.groupValues[4].takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: 1f
            if (saturation !in 0f..1f || lightness !in 0f..1f || alpha !in 0f..1f) return null

            val normalizedHue = ((hue % 360f) + 360f) % 360f
            val chroma = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
            val x = chroma * (1f - kotlin.math.abs((normalizedHue / 60f) % 2f - 1f))
            val (r, g, b) = when (normalizedHue) {
                in 0f..<60f -> Triple(chroma, x, 0f)
                in 60f..<120f -> Triple(x, chroma, 0f)
                in 120f..<180f -> Triple(0f, chroma, x)
                in 180f..<240f -> Triple(0f, x, chroma)
                in 240f..<300f -> Triple(x, 0f, chroma)
                else -> Triple(chroma, 0f, x)
            }
            val m = lightness - chroma / 2f
            return Color(r + m, g + m, b + m, alpha)
        }
    }
}

/**
 * Color blending mode. Ported from mbgl::gfx::ColorMode.
 */
data class ColorMode(
    val equation: ColorBlendEquationType = ColorBlendEquationType.Add,
    val srcFactor: ColorBlendFactorType = ColorBlendFactorType.One,
    val dstFactor: ColorBlendFactorType = ColorBlendFactorType.Zero,
) {
    companion object {
        /** Straight alpha blending: src over dst. */
        val AlphaBlended = ColorMode(
            ColorBlendEquationType.Add,
            ColorBlendFactorType.One,
            ColorBlendFactorType.OneMinusSrcAlpha,
        )

        /** Additive blending. */
        val Additive = ColorMode(
            ColorBlendEquationType.Add,
            ColorBlendFactorType.One,
            ColorBlendFactorType.One,
        )

        /** Replace (no blending). */
        val Replace = ColorMode(
            ColorBlendEquationType.Add,
            ColorBlendFactorType.One,
            ColorBlendFactorType.Zero,
        )
    }
}

/**
 * Depth test mode. Ported from mbgl::gfx::DepthMode.
 */
data class DepthMode(
    val function: DepthFunctionType = DepthFunctionType.LessEqual,
    val mask: DepthMaskType = DepthMaskType.ReadWrite,
    val range: ClosedFloatingPointRange<Float> = 0.0f..1.0f,
) {
    companion object {
        val Disabled = DepthMode(DepthFunctionType.Always, DepthMaskType.ReadOnly)
        val ReadOnly = DepthMode(DepthFunctionType.LessEqual, DepthMaskType.ReadOnly)
        val ReadWrite = DepthMode(DepthFunctionType.LessEqual, DepthMaskType.ReadWrite)
    }
}

/**
 * Stencil test mode. Ported from mbgl::gfx::StencilMode.
 */
data class StencilMode(
    val function: StencilFunctionType = StencilFunctionType.Always,
    val ref: Int = 0,
    val mask: Int = 0xFF,
    val opFail: StencilOpType = StencilOpType.Keep,
    val opZFail: StencilOpType = StencilOpType.Keep,
    val opZPass: StencilOpType = StencilOpType.Keep,
) {
    companion object {
        val Disabled = StencilMode()
    }
}

/** Cull face mode. Ported from mbgl::gfx::CullFaceMode. */
data class CullFaceMode(
    val side: CullFaceSideType = CullFaceSideType.Back,
    val winding: CullFaceWindingType = CullFaceWindingType.CounterClockwise,
) {
    companion object {
        val Disabled = CullFaceMode(CullFaceSideType.Back, CullFaceWindingType.CounterClockwise)
    }
}
