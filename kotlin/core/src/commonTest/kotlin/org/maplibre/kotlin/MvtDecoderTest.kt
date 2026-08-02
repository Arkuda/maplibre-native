package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.MvtDecoder
import org.maplibre.kotlin.tile.TileValue

/**
 * Hand-builds protobuf bytes for vector_tile.proto and checks the decoder.
 * Mirrors the example in the proto comment:
 * MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath
 * Encoded as: [ 3 6 18 5 6 12 22 15 ]
 */
class MvtDecoderTest {

    // --- protobuf helpers ---

    private fun varint(v: Long): ByteArray {
        var value = v
        val out = mutableListOf<Byte>()
        while (true) {
            val b = (value and 0x7F).toInt()
            value = value ushr 7
            if (value != 0L) {
                out.add((b or 0x80).toByte())
            } else {
                out.add(b.toByte())
                break
            }
        }
        return out.toByteArray()
    }

    private fun tag(field: Int, wireType: Int): ByteArray = varint((field shl 3 or wireType).toLong())

    private fun bytesField(field: Int, content: ByteArray): ByteArray =
        tag(field, 2) + varint(content.size.toLong()) + content

    private fun stringField(field: Int, s: String): ByteArray =
        bytesField(field, s.toByteArray(Charsets.UTF_8))

    private fun varintField(field: Int, v: Long): ByteArray = tag(field, 0) + varint(v)

    private fun packedField(field: Int, values: List<Long>): ByteArray {
        val body = values.flatMap { varint(it).toList() }.toByteArray()
        return tag(field, 2) + varint(body.size.toLong()) + body
    }

    private fun concat(vararg parts: ByteArray): ByteArray =
        parts.fold(ByteArray(0)) { acc, p -> acc + p }

    // --- tests ---

    @Test
    fun decodesKnownGeometryExample() {
        // feature: MoveTo(3,6), LineTo(8,12), LineTo(20,34), ClosePath
        // Real varint encoding:
        //   9  = MoveTo, count=1        (1 | (1<<3))
        //   6  = zigzag(+3)             3  -> (3<<1)^0 = 6
        //   12 = zigzag(+6)             6  -> (6<<1)^0 = 12
        //   18 = LineTo, count=2        (2 | (2<<3))
        //   10 = zigzag(+5)             5  -> (5<<1)^0 = 10
        //   12 = zigzag(+6)
        //   24 = zigzag(+12)           12  -> (12<<1)^0 = 24
        //   44 = zigzag(+22)           22  -> (22<<1)^0 = 44
        //   15 = ClosePath
        val geometry = listOf(9L, 6L, 12L, 18L, 10L, 12L, 24L, 44L, 15L)
        val feature = concat(
            varintField(3, 2), // type = LineString
            packedField(4, geometry),
        )

        val layer = concat(
            varintField(15, 2), // version = 2
            stringField(1, "roads"),
            bytesField(2, feature),
            varintField(5, 4096), // extent
        )

        val tile = bytesField(3, layer)

        val decoded = MvtDecoder.decode(tile)
        val roads = decoded.getLayer("roads")!!
        assertEquals("roads", roads.name)
        assertEquals(2, roads.version)
        assertEquals(4096, roads.extent)
        assertEquals(1, roads.features.size)

        val f = roads.features[0]
        assertEquals(FeatureType.LINESTRING, f.type)
        assertEquals(1, f.geometry.size)
        val line = f.geometry[0]
        // MoveTo(1) + LineTo(2) = 3 vertices; ClosePath closes the ring without
        // adding a vertex (the closing point equals the first one)
        assertEquals(3, line.size)
        assertEquals(3.0, line[0].x)
        assertEquals(6.0, line[0].y)
        assertEquals(8.0, line[1].x)
        assertEquals(12.0, line[1].y)
        assertEquals(20.0, line[2].x)
        assertEquals(34.0, line[2].y)
    }

    @Test
    fun decodesPropertiesFromDictionaries() {
        // layer keys: ["name"], values: ["main"]
        // feature: point with tags [0,0], MoveTo(1,2)
        // geometry: 9 = MoveTo count 1, 2 = zigzag(+1), 4 = zigzag(+2)
        val feature = concat(
            varintField(1, 42), // id
            varintField(3, 1), // type = Point
            packedField(2, listOf(0, 0).map { it.toLong() }), // tags
            packedField(4, listOf(9L, 2L, 4L)), // MoveTo(1,2)
        )

        val layer = concat(
            varintField(15, 2),
            stringField(1, "pois"),
            bytesField(2, feature),
            stringField(3, "name"),
            bytesField(4, stringField(1, "main")),
            varintField(5, 4096),
        )

        val tile = bytesField(3, layer)
        val decoded = MvtDecoder.decode(tile)
        val pois = decoded.getLayer("pois")!!
        val f = pois.features[0]
        assertEquals(42L, f.id)
        assertEquals(FeatureType.POINT, f.type)
        assertEquals("main", (f.properties["name"] as TileValue.Str).value)
        assertEquals(1, f.geometry.size)
        assertEquals(1.0, f.geometry[0][0].x)
        assertEquals(2.0, f.geometry[0][0].y)
    }

    @Test
    fun decodesValueVariants() {
        // values: string, float, double, int, uint, sint, bool
        // float_value = field 2, wireType 5 (fixed32)
        // double_value = field 3, wireType 1 (fixed64)
        val values = listOf(
            stringField(1, "hello"),
            tag(2, 5) + floatToBytes(1.5f), // float_value: fixed32
            tag(3, 1) + doubleToBytes(2.25), // double_value: fixed64
            varintField(4, 7), // int_value: varint
            varintField(5, 8), // uint_value: varint
            varintField(6, 3), // sint_value: varint (zigzag 3 => -2)
            varintField(7, 1), // bool_value: varint
        )
        val layer = concat(
            varintField(15, 2),
            stringField(1, "vals"),
            *values.map { bytesField(4, it) }.toTypedArray(),
        )
        val tile = bytesField(3, layer)
        // no features; just ensure the values parse without error
        val decoded = MvtDecoder.decode(tile)
        assertEquals(1, decoded.layers.size)
        assertTrue(decoded.getLayer("vals")!!.features.isEmpty())
    }

    @Test
    fun emptyTile() {
        val decoded = MvtDecoder.decode(byteArrayOf())
        assertTrue(decoded.layers.isEmpty())
    }

    private fun floatToBytes(v: Float): ByteArray {
        val bits = v.toRawBits()
        return byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits shr 8) and 0xFF).toByte(),
            ((bits shr 16) and 0xFF).toByte(),
            ((bits shr 24) and 0xFF).toByte(),
        )
    }

    private fun doubleToBytes(v: Double): ByteArray {
        val bits = v.toRawBits()
        val out = ByteArray(8)
        for (i in 0 until 8) {
            out[i] = ((bits shr (i * 8)) and 0xFF).toByte()
        }
        return out
    }
}
