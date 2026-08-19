package org.maplibre.kotlin.gfx

import kotlinx.cinterop.*
import org.maplibre.kotlin.gfx.VertexData.*
import org.maplibre.kotlin.gfx.Types.*

/**
 * Ported from mbgl::gfx::VertexVector and mbgl::gfx::IndexVector.
 */
class VertexVector(val data: ByteBuffer) {
    val elements: Int get() = data.capacity() / 4 // This is simplified.
}

class IndexVector(val data: ByteBuffer) {
    val elements: Int get() = data.capacity() / 2 // This is simplified.
}

class VertexAttributeArray {
    private val attributes = mutableMapOf<String, VertexAttribute>()

    fun set(name: String): VertexAttribute {
        return attributes.getOrPut(name) {
            VertexAttribute(AttributeDataType.Float, 0) // Default, will be updated
        }
    }

    fun get(name: String): VertexAttribute? = attributes[name]
}

class VertexAttribute(
    val dataType: AttributeDataType,
    var offset: Int = 0,
    var vertexStride: Int = 0,
    var vertexBufferResource: Any? = null
) {
    fun setSharedRawData(data: ByteBuffer, offset: Int, count: Int, type: AttributeDataType) {
        this.vertexBufferResource = data
        this.vertexStride = vertexStride
        this.offset = offset
    }
}

open class SegmentBase

class DrawSegment(
    val mode: DrawMode,
    val segment: SegmentBase
)

data class FillLayoutVertex(val a1: Array<Short>)

data class LineLayoutVertex(
    val a1: Array<Short>,
    val a2: Array<Byte>
)
