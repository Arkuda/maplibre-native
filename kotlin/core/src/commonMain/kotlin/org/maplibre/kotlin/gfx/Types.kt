package org.maplibre.kotlin.gfx

/**
 * Graphics types shared by all backends. Ported from
 * mbgl::gfx::types.hpp and mbgl::gfx::gfx_types.hpp.
 */

/** Color blending equation type. */
enum class ColorBlendEquationType {
    Add,           // O = sS + dD
    Subtract,      // O = sS - dD
    ReverseSubtract, // O = dD - sS
}

/** Color blending factor type. */
enum class ColorBlendFactorType {
    Zero, One,
    SrcColor, OneMinusSrcColor,
    SrcAlpha, OneMinusSrcAlpha,
    DstAlpha, OneMinusDstAlpha,
    DstColor, OneMinusDstColor,
    SrcAlphaSaturate,
    ConstantColor, OneMinusConstantColor,
    ConstantAlpha, OneMinusConstantAlpha,
}

/** Depth function type. */
enum class DepthFunctionType {
    Never, Less, Equal, LessEqual, Greater, NotEqual, GreaterEqual, Always,
}

/** Depth buffer masking type. */
enum class DepthMaskType {
    ReadOnly, ReadWrite,
}

/** Stencil function type. */
enum class StencilFunctionType {
    Never, Less, Equal, LessEqual, Greater, NotEqual, GreaterEqual, Always,
}

/** Stencil operation type. */
enum class StencilOpType {
    Zero, Keep, Replace, Increment, Decrement, Invert, IncrementWrap, DecrementWrap,
}

/** Cull face side. */
enum class CullFaceSideType { Front, Back, FrontAndBack }

/** Orientation of front-facing polygons. */
enum class CullFaceWindingType { Clockwise, CounterClockwise }

/** Buffer usage type. */
enum class BufferUsageType { StreamDraw, StaticDraw, DynamicDraw }

/** Texture pixel type. */
enum class TexturePixelType { RGBA, Alpha, Stencil, Depth, Luminance }

/** Texture channel data type. */
enum class TextureChannelDataType { UnsignedByte, HalfFloat, Float }

/** Texture mip map type. */
enum class TextureMipMapType { No, Yes }

/** Texture filter type. */
enum class TextureFilterType { Nearest, Linear }

/** Texture wrap mode. */
enum class TextureWrapType { Clamp, Repeat }

/** Render buffer pixel type. */
enum class RenderbufferPixelType { RGBA, Depth, DepthStencil }

/** Draw mode for primitives. */
enum class DrawMode {
    Points,
    Lines,
    LineLoop,
    LineStrip,
    Triangles,
    TriangleStrip,
    TriangleFan,
}

/** Attribute data type. */
enum class AttributeDataType {
    Byte, UnsignedByte, Short, UnsignedShort, Int, UnsignedInt,
    Float, HalfFloat, Double, Invalid,
}

/** 2D size. */
data class Size(val width: Int, val height: Int) {
    val area: Int get() = width * height

    companion object {
        val Zero = Size(0, 0)
    }
}
