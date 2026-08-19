package org.maplibre.kotlin.gfx

import kotlinx.cinterop.*
import org.maplibre.kotlin.gfx.VertexData.*
import org.maplibre.kotlin.gfx.Types.*

/**
 * Ported from mbgl::gfx::Drawable.
 */
abstract class Drawable(name: String) {
    var name: String = name
    var uniqueID: SimpleIdentity = SimpleIdentity()
    var shader: ShaderProgram? = null
    var colorMode: ColorMode = ColorMode.Disabled
    var depthMode: DepthMaskType = DepthMaskType(0)
    var stencilMode: StencilMode = StencilMode.Disabled
    var cullFaceMode: CullFaceMode = CullFaceMode.Disabled
    var enabled: Boolean = true
    var enableColor: Boolean = true
    var enableStencil: Boolean = false
    var enableDepth: Boolean = true
    var is3D: Boolean = false
    var isCustom: Boolean = false
    var lineWidth: Int = 1
    var renderPass: RenderPass = RenderPass.Opaque
    var vertexAttributes: VertexAttributeArray = VertexAttributeArray()
    var instanceAttributes: VertexAttributeArray = VertexAttributeArray()
    var textures: Array<Texture2DPtr> = emptyArray()
    var tweakers: MutableList<DrawableTweakerPtr> = mutableListOf()
    var layerTweaker: DrawableTweakerPtr? = null
    var origin: Point<Double>? = null
    var type: Long = 0
    var uboIndex: Int = 0
    var drawableData: UniqueDrawableData = UniqueDrawableData()

    protected constructor(name: String)

    public constructor(name: String) : this(name)

    fun getID() = uniqueID
    fun getName() = name
    fun setName(value: String) { name = value }
    fun getShader() = shader
    fun setShader(value: ShaderProgram?) { shader = value }
    fun getRenderPass() = renderPass
    fun setRenderPass(value: RenderPass) { renderPass = value }
    fun hasRenderPass(value: RenderPass) = (renderPass.ordinal and value.ordinal) != 0
    fun hasAllRenderPasses(value: RenderPass) = (renderPass.ordinal and value.ordinal) == value.ordinal
    fun getLineWidth() = lineWidth
    fun setLineWidth(value: Int) { lineWidth = value }
    fun getTexture(id: Int) = textures[id]
    fun setTextures(value: Array<Texture2DPtr>) { textures = value }
    fun setTexture(texture: Texture2DPtr, id: Int) { /* ... */ }
    fun getEnabled() = enabled
    fun setEnabled(value: Boolean) { enabled = value }
    fun getEnableColor() = enableColor
    fun setEnableColor(value: Boolean) { enableColor = value }
    fun getEnableStencil() = enableStencil
    fun setEnableStencil(value: Boolean) { enableStencil = value }
    fun getEnableDepth() = enableDepth
    fun setEnableDepth(value: Boolean) { enableDepth = value }
    fun getSubLayerIndex() = 0
    fun setSubLayerIndex(value: Int) { /* ... */ }
    fun getDepthType() = depthMode
    fun setDepthType(value: DepthMaskType) { depthMode = value }
    fun getIs3D() = is3D
    fun setIs3D(value: Boolean) { is3D = value }
    fun getIsCustom() = isCustom
    fun setIsCustom(value: Boolean) { isCustom = value }
    fun getTileID() = null
    fun setTileID(value: OverscaledTileID) { /* ... */ }
    fun getCullFaceMode() = cullFaceMode
    fun setCullFaceMode(value: CullFaceMode) { cullFaceMode = value }
    fun getColorMode() = colorMode
    fun setColorMode(value: ColorMode) { colorMode = value }
    fun getVertexAttributes() = vertexAttributes
    fun setVertexAttributes(value: VertexAttributeArray) { vertexAttributes = value }
    fun getInstanceAttributes() = instanceAttributes
    fun setInstanceAttributes(value: VertexAttributeArray) { instanceAttributes = value }
    fun getTweakers() = tweakers
    fun addTweaker(value: DrawableTweakerPtr) { tweakers.add(value) }
    fun setTweakers(value: MutableList<DrawableTweakerPtr>) { tweakers = value }
    fun clearTweakers() { tweakers.clear() }
    fun getUBOIndex() = uboIndex
    fun setUBOIndex(value: Int) { uboIndex = value }
    fun getOrigin() = origin
    fun setOrigin(value: Point<Double>?) { origin = value }
    fun getData() = drawableData
    fun setData(value: UniqueDrawableData) { drawableData = value }
    fun getBinders() = null
    fun setBinders(value: PaintPropertyBindersBase?) { /* ... */ }
    fun getRenderTile() = null
    fun getBucket() = null
    fun setRenderTile(tile: RenderTile, id: OverscaledTileID) { /* ... */ }

    abstract fun draw(paintParameters: PaintParameters)

    abstract fun updateVertexAttributes(
        vertexAttrs: VertexAttributeArray,
        vertexCount: Int,
        drawMode: DrawMode,
        indexes: IndexVectorBasePtr?,
        segments: Array<DrawSegment>?,
        segmentCount: Int
    )
}

/**
 * Ported from mbgl::gfx::FillDrawable.
 */
class FillDrawable(name: String) : Drawable(name) {
    var vertices: VertexVector = VertexVector(ByteBuffer.allocate(0))
    var triangles: IndexVector = IndexVector(ByteBuffer.allocate(0))
    var segments: MutableList<DrawSegment> = mutableListOf()

    override fun draw(paintParameters: PaintParameters) {
        // To be implemented
    }

    override fun updateVertexAttributes(
        vertexAttrs: VertexAttributeArray,
        vertexCount: Int,
        drawMode: DrawMode,
        indexes: IndexVectorBasePtr?,
        segments: Array<DrawSegment>?,
        segmentCount: Int
    ) {
        // To be implemented
    }
}

/**
 * Ported from mbgl::gfx::LineDrawable.
 */
class LineDrawable(name: String) : Drawable(name) {
    var vertices: VertexVector = VertexVector(ByteBuffer.allocate(0))
    var triangles: IndexVector = IndexVector(ByteBuffer.allocate(0))
    var segments: MutableList<DrawSegment> = mutableListOf()

    override fun draw(paintParameters: PaintParameters) {
        // To be implemented
    }

    override fun updateVertexAttributes(
        vertexAttrs: VertexAttributeArray,
        vertexCount: Int,
        drawMode: DrawMode,
        indexes: IndexVectorBasePtr?,
        segments: Array<DrawSegment>?,
        segmentCount: Int
    ) {
        // To be implemented
    }
}
