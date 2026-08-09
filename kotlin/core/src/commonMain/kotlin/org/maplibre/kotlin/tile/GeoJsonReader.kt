package org.maplibre.kotlin.tile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.kotlin.style.GeoJsonGeometry

/** A GeoJSON feature with typed properties, ready for tile conversion. */
data class GeoJsonFeature(
    val id: Long?,
    val geometry: GeoJsonGeometry,
    val properties: Map<String, TileValue>,
)

/**
 * Parses GeoJSON text (FeatureCollection, Feature, or bare geometry) into
 * typed features. Coordinates are kept as lon/lat (x/y) as written; the
 * tile conversion lives in [GeoJsonTileBuilder].
 */
object GeoJsonReader {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<GeoJsonFeature> = parseElement(json.parseToJsonElement(text))

    private fun parseElement(element: JsonElement): List<GeoJsonFeature> {
        val obj = element.jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "FeatureCollection" -> obj["features"]?.jsonArray?.mapNotNull { parseFeature(it) } ?: emptyList()
            "Feature" -> listOfNotNull(parseFeature(element))
            "Point", "LineString", "Polygon", "MultiPoint", "MultiLineString", "MultiPolygon", "GeometryCollection" ->
                listOf(GeoJsonFeature(null, parseGeometry(obj) ?: return emptyList(), emptyMap()))
            else -> emptyList()
        }
    }

    private fun parseFeature(element: JsonElement): GeoJsonFeature? {
        val obj = element.jsonObject
        val geometry = parseGeometry(obj["geometry"]?.jsonObject ?: return null) ?: return null
        val properties = parseProperties(obj["properties"])
        val id = (obj["id"] as? JsonPrimitive)?.let { it.content.toLongOrNull() ?: it.content.hashCode().toLong() }
        return GeoJsonFeature(id, geometry, properties)
    }

    private fun parseGeometry(obj: kotlinx.serialization.json.JsonObject): GeoJsonGeometry? {
        return when (obj["type"]?.jsonPrimitive?.content) {
            "Point" -> {
                val c = obj["coordinates"]?.jsonArray ?: return null
                GeoJsonGeometry.Point(c[0].jsonPrimitive.doubleOrNull ?: 0.0, c[1].jsonPrimitive.doubleOrNull ?: 0.0)
            }
            "LineString" -> GeoJsonGeometry.LineString(
                obj["coordinates"]?.jsonArray?.mapNotNull { parsePoint(it) } ?: emptyList(),
            )
            "Polygon" -> GeoJsonGeometry.Polygon(
                obj["coordinates"]?.jsonArray?.mapNotNull { ring ->
                    GeoJsonGeometry.LineString(ring.jsonArray.mapNotNull { parsePoint(it) })
                } ?: emptyList(),
            )
            else -> null
        }
    }

    private fun parsePoint(element: JsonElement): GeoJsonGeometry.Point {
        val arr = element.jsonArray
        return GeoJsonGeometry.Point(
            arr[0].jsonPrimitive.doubleOrNull ?: 0.0,
            arr[1].jsonPrimitive.doubleOrNull ?: 0.0,
        )
    }

    private fun parseProperties(element: JsonElement?): Map<String, TileValue> {
        if (element == null || element !is kotlinx.serialization.json.JsonObject) return emptyMap()
        return element.mapValues { (_, v) ->
            val p = v.jsonPrimitive
            p.booleanOrNull?.let { TileValue.Bool(it) }
                ?: p.doubleOrNull?.let { TileValue.Num(it) }
                ?: TileValue.Str(p.content)
        }
    }
}
