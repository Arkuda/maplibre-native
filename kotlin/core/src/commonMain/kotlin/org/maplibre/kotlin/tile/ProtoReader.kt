package org.maplibre.kotlin.tile

/**
 * Minimal protobuf wire-format reader used by the MVT decoder.
 * Mirrors the protozero reader used by MapLibre.
 */
class ProtoReader(private val data: ByteArray) {
    private var pos = 0

    val isEnd: Boolean get() = pos >= data.size

    fun readTag(): Int {
        val value = readVarint32()
        return value
    }

    /** Reads a varint as Long. */
    fun readVarint64(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift >= 64) throw IllegalStateException("varint too long")
        }
        return result
    }

    /** Reads a varint as Int. */
    fun readVarint32(): Int = readVarint64().toInt()

    /** Reads a zigzag-encoded sint (int32). */
    fun readSVarint32(): Int {
        val raw = readVarint32()
        return (raw ushr 1) xor -(raw and 1)
    }

    /** Reads a zigzag-encoded sint (int64). */
    fun readSVarint64(): Long {
        val raw = readVarint64()
        return (raw ushr 1) xor -(raw and 1)
    }

    /** Skips a field with the given wire type. */
    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint64() // varint
            1 -> pos += 8 // 64-bit
            2 -> {
                val len = readVarint32()
                pos += len
            }
            5 -> pos += 4 // 32-bit
            else -> throw IllegalStateException("Unsupported wire type $wireType")
        }
    }

    /** Reads a length-delimited field (bytes/string/message). */
    fun readBytes(): ByteArray {
        val len = readVarint32()
        if (len < 0 || pos + len > data.size) throw IllegalStateException("Invalid length $len")
        val result = data.copyOfRange(pos, pos + len)
        pos += len
        return result
    }

    /** Reads a float (32-bit little-endian). */
    fun readFloat(): Float {
        val bits = readFixed32()
        return Float.fromBits(bits)
    }

    /** Reads a double (64-bit little-endian). */
    fun readDouble(): Double {
        val lo = readFixed32()
        val hi = readFixed32()
        return Double.fromBits((hi.toLong() shl 32) or (lo.toLong() and 0xFFFFFFFFL))
    }

    private fun readFixed32(): Int {
        val b0 = data[pos++].toInt() and 0xFF
        val b1 = data[pos++].toInt() and 0xFF
        val b2 = data[pos++].toInt() and 0xFF
        val b3 = data[pos++].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}

/** Field tags for vector_tile.proto. */
object VectorTileProto {
    const val TILE_LAYERS = 3

    const val LAYER_VERSION = 15
    const val LAYER_NAME = 1
    const val LAYER_FEATURES = 2
    const val LAYER_KEYS = 3
    const val LAYER_VALUES = 4
    const val LAYER_EXTENT = 5

    const val FEATURE_ID = 1
    const val FEATURE_TAGS = 2
    const val FEATURE_TYPE = 3
    const val FEATURE_GEOMETRY = 4

    const val VALUE_STRING = 1
    const val VALUE_FLOAT = 2
    const val VALUE_DOUBLE = 3
    const val VALUE_INT = 4
    const val VALUE_UINT = 5
    const val VALUE_SINT = 6
    const val VALUE_BOOL = 7
}

/** Geometry type from the proto. */
enum class GeomType { UNKNOWN, POINT, LINESTRING, POLYGON }
