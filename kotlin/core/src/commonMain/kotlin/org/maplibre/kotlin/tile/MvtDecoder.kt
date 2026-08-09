package org.maplibre.kotlin.tile

/**
 * Decoded geometry types. Mirrors mbgl::FeatureType / GeometryCollection.
 */
enum class FeatureType { UNKNOWN, POINT, LINESTRING, POLYGON }

/** A point in tile coordinates. */
data class TilePoint(val x: Double, val y: Double)

/** Geometry of a feature: list of rings/lines, each a list of points. */
typealias GeometryCollection = List<List<TilePoint>>

/** Property value of a feature (MVT value variant). */
sealed class TileValue {
    object Null : TileValue()
    data class Str(val value: String) : TileValue()
    data class Num(val value: Double) : TileValue()
    data class Bool(val value: Boolean) : TileValue()
}

/** A single feature in a tile layer. */
data class TileFeature(
    val id: Long?,
    val type: FeatureType,
    val properties: Map<String, TileValue>,
    val geometry: GeometryCollection,
)

/** A layer inside a tile. */
data class TileLayer(
    val name: String,
    val version: Int,
    val extent: Int,
    val features: List<TileFeature>,
)

/** A decoded vector tile. */
data class VectorTileData(
    val layers: Map<String, TileLayer>,
) {
    fun getLayer(name: String): TileLayer? = layers[name]
}

/**
 * Decodes Mapbox Vector Tile (.pbf) bytes into [VectorTileData].
 * Implements vector_tile.proto (v1/v2): layers, features, keys/values
 * dictionaries, and delta-encoded command geometry.
 */
object MvtDecoder {

    fun decode(data: ByteArray): VectorTileData {
        val reader = ProtoReader(data)
        val layers = LinkedHashMap<String, TileLayer>()

        while (!reader.isEnd) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wireType = tag and 0x07
            when (field) {
                VectorTileProto.TILE_LAYERS -> {
                    val layer = parseLayer(ProtoReader(reader.readBytes()))
                    layers[layer.name] = layer
                }
                else -> reader.skip(wireType)
            }
        }
        return VectorTileData(layers)
    }

    private fun parseLayer(reader: ProtoReader): TileLayer {
        var version = 1
        var name = ""
        var extent = 4096
        val features = mutableListOf<ByteArray>()
        val keys = mutableListOf<String>()
        val values = mutableListOf<TileValue>()

        while (!reader.isEnd) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wireType = tag and 0x07
            when (field) {
                VectorTileProto.LAYER_VERSION -> version = reader.readVarint32()
                VectorTileProto.LAYER_NAME -> name = reader.readBytes().decodeToString()
                VectorTileProto.LAYER_FEATURES -> features.add(reader.readBytes())
                VectorTileProto.LAYER_KEYS -> keys.add(reader.readBytes().decodeToString())
                VectorTileProto.LAYER_VALUES -> values.add(parseValue(ProtoReader(reader.readBytes())))
                VectorTileProto.LAYER_EXTENT -> extent = reader.readVarint32()
                else -> reader.skip(wireType)
            }
        }

        val parsedFeatures = features.map { parseFeature(ProtoReader(it), keys, values) }
        return TileLayer(name, version, extent, parsedFeatures)
    }

    private fun parseValue(reader: ProtoReader): TileValue {
        var result: TileValue = TileValue.Null
        while (!reader.isEnd) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wireType = tag and 0x07
            when (field) {
                VectorTileProto.VALUE_STRING -> result = TileValue.Str(reader.readBytes().decodeToString())
                VectorTileProto.VALUE_FLOAT -> result = TileValue.Num(reader.readFloat().toDouble())
                VectorTileProto.VALUE_DOUBLE -> result = TileValue.Num(reader.readDouble())
                VectorTileProto.VALUE_INT -> result = TileValue.Num(reader.readVarint64().toDouble())
                VectorTileProto.VALUE_UINT -> result = TileValue.Num(reader.readVarint64().toDouble())
                VectorTileProto.VALUE_SINT -> result = TileValue.Num(reader.readSVarint64().toDouble())
                VectorTileProto.VALUE_BOOL -> result = TileValue.Bool(reader.readVarint32() != 0)
                else -> reader.skip(wireType)
            }
        }
        return result
    }

    private fun parseFeature(
        reader: ProtoReader,
        keys: List<String>,
        values: List<TileValue>,
    ): TileFeature {
        var id: Long? = null
        var type = GeomType.UNKNOWN
        val tags = mutableListOf<Int>()
        val geometryBytes = mutableListOf<ByteArray>()

        while (!reader.isEnd) {
            val tag = reader.readTag()
            val field = tag ushr 3
            val wireType = tag and 0x07
            when (field) {
                VectorTileProto.FEATURE_ID -> id = reader.readVarint64()
                VectorTileProto.FEATURE_TAGS -> {
                    // packed uint32
                    if (wireType == 2) {
                        val packed = ProtoReader(reader.readBytes())
                        while (!packed.isEnd) tags.add(packed.readVarint32())
                    } else {
                        tags.add(reader.readVarint32())
                    }
                }
                VectorTileProto.FEATURE_TYPE -> type = geomType(reader.readVarint32())
                VectorTileProto.FEATURE_GEOMETRY -> {
                    // packed uint32 commands
                    if (wireType == 2) {
                        geometryBytes.add(reader.readBytes())
                    }
                }
                else -> reader.skip(wireType)
            }
        }

        // decode properties from tags: [keyIdx, valueIdx, ...]
        val properties = LinkedHashMap<String, TileValue>()
        var i = 0
        while (i + 1 < tags.size) {
            val keyIdx = tags[i]
            val valueIdx = tags[i + 1]
            if (keyIdx < keys.size) {
                properties[keys[keyIdx]] = if (valueIdx < values.size) values[valueIdx] else TileValue.Null
            }
            i += 2
        }

        // decode geometry commands from packed bytes
        val geometry = decodeGeometry(geometryBytes, type)

        return TileFeature(
            id = id,
            type = featureType(type),
            properties = properties,
            geometry = geometry,
        )
    }

    private fun geomType(v: Int): GeomType = when (v) {
        1 -> GeomType.POINT
        2 -> GeomType.LINESTRING
        3 -> GeomType.POLYGON
        else -> GeomType.UNKNOWN
    }

    private fun featureType(g: GeomType): FeatureType = when (g) {
        GeomType.POINT -> FeatureType.POINT
        GeomType.LINESTRING -> FeatureType.LINESTRING
        GeomType.POLYGON -> FeatureType.POLYGON
        GeomType.UNKNOWN -> FeatureType.UNKNOWN
    }

    /**
     * Decodes the delta-encoded geometry command stream.
     * Commands: 1 = MoveTo(2 params), 2 = LineTo(2 params), 15 = ClosePath(0).
     * Parameters are zigzag-encoded deltas from the previous point.
     */
    private fun decodeGeometry(chunks: List<ByteArray>, type: GeomType): GeometryCollection {
        val params = mutableListOf<Int>()
        for (chunk in chunks) {
            val r = ProtoReader(chunk)
            while (!r.isEnd) params.add(r.readVarint32())
        }

        val result = mutableListOf<List<TilePoint>>()
        var current = mutableListOf<TilePoint>()
        var cursorX = 0L
        var cursorY = 0L
        var i = 0

        fun closeRing() {
            if (current.size > 0) {
                result.add(current)
                current = mutableListOf()
            }
        }

        while (i < params.size) {
            val cmdInt = params[i]
            i++
            val cmd = cmdInt and 0x07
            val count = cmdInt ushr 3

            when (cmd) {
                1 -> { // MoveTo
                    closeRing()
                    repeat(count) {
                        val dx = params[i++]
                        val dy = params[i++]
                        cursorX += zigzag(dx)
                        cursorY += zigzag(dy)
                        current.add(TilePoint(cursorX.toDouble(), cursorY.toDouble()))
                    }
                }
                2 -> { // LineTo
                    repeat(count) {
                        val dx = params[i++]
                        val dy = params[i++]
                        cursorX += zigzag(dx)
                        cursorY += zigzag(dy)
                        current.add(TilePoint(cursorX.toDouble(), cursorY.toDouble()))
                    }
                }
                7 -> { // ClosePath — encoded as (1<<3)|7 = 15
                    closeRing()
                }
                else -> throw IllegalStateException("Unknown geometry command $cmd")
            }
        }
        closeRing()

        // Points: each MoveTo starts a new point
        if (type == GeomType.POINT && result.size > 1) {
            // flatten: each ring of a point feature is a single point
            return result.filter { it.isNotEmpty() }.map { listOf(it.first()) }
        }
        return result
    }

    private fun zigzag(v: Int): Long = (v.toLong() ushr 1) xor -(v.toLong() and 1)
}
