package org.maplibre.kotlin.style

import org.maplibre.kotlin.expression.ExpressionParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses a MapLibre style JSON document into the typed [StyleSpec] model.
 *
 * The JSON format is the on-disk/wire interchange format of the MapLibre
 * style spec — it cannot disappear entirely. What this parser buys us is that
 * JSON is consumed exactly once, at the boundary, and from that point on the
 * core works with plain Kotlin objects. Nothing is re-serialized to a JSON
 * string to cross the platform/engine boundary.
 */
object StyleJsonParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(styleJson: String): StyleSpec {
        val root = json.parseToJsonElement(styleJson).jsonObject
        val sources = root["sources"]?.jsonObject?.mapValues { (id, el) -> parseSource(id, el) } ?: emptyMap()
        val layers = root["layers"]?.jsonArray?.map { parseLayer(it) } ?: emptyList()
        val light = root["light"]?.let { parseLight(it) }
        return StyleSpec(
            version = root["version"]?.jsonPrimitive?.intOrNull ?: 8,
            name = root["name"]?.jsonPrimitive?.contentOrNull(),
            sources = sources,
            layers = layers,
            sprite = root["sprite"]?.jsonPrimitive?.contentOrNull(),
            glyphs = root["glyphs"]?.jsonPrimitive?.contentOrNull(),
            light = light,
        )
    }

    private fun parseSource(id: String, element: JsonElement): SourceSpec {
        val obj = element.jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "vector" -> SourceSpec.Vector(
                id = id,
                url = obj["url"]?.jsonPrimitive?.contentOrNull(),
                tiles = obj["tiles"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                minzoom = obj["minzoom"]?.jsonPrimitive?.intOrNull ?: 0,
                maxzoom = obj["maxzoom"]?.jsonPrimitive?.intOrNull ?: 22,
                attribution = obj["attribution"]?.jsonPrimitive?.contentOrNull(),
            )
            "raster" -> SourceSpec.Raster(
                id = id,
                url = obj["url"]?.jsonPrimitive?.contentOrNull(),
                tiles = obj["tiles"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                tileSize = obj["tileSize"]?.jsonPrimitive?.intOrNull ?: 512,
                attribution = obj["attribution"]?.jsonPrimitive?.contentOrNull(),
            )
            "geojson" -> SourceSpec.GeoJson(
                id = id,
                geometry = obj["data"]?.let { parseGeometry(it) },
                attribution = obj["attribution"]?.jsonPrimitive?.contentOrNull(),
            )
            else -> SourceSpec.Vector(id = id)
        }
    }

    private fun parseGeometry(element: JsonElement): GeoJsonGeometry? {
        val obj = element.jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "Point" -> {
                val coords = obj["coordinates"]?.jsonArray ?: return null
                GeoJsonGeometry.Point(
                    coords[0].jsonPrimitive.doubleOrNull ?: 0.0,
                    coords[1].jsonPrimitive.doubleOrNull ?: 0.0,
                )
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

    private fun parsePoint(element: JsonElement): GeoJsonGeometry.Point? {
        val arr = element.jsonArray
        if (arr.size < 2) return null
        return GeoJsonGeometry.Point(
            arr[0].jsonPrimitive.doubleOrNull ?: 0.0,
            arr[1].jsonPrimitive.doubleOrNull ?: 0.0,
        )
    }

    private fun parseLayer(element: JsonElement): LayerSpec {
        val obj = element.jsonObject
        val type = parseLayerType(obj["type"]?.jsonPrimitive?.content ?: "fill")
        return LayerSpec(
            id = obj["id"]?.jsonPrimitive?.content ?: "",
            type = type,
            source = obj["source"]?.jsonPrimitive?.contentOrNull(),
            sourceLayer = obj["source-layer"]?.jsonPrimitive?.contentOrNull(),
            minzoom = obj["minzoom"]?.jsonPrimitive?.floatOrNull,
            maxzoom = obj["maxzoom"]?.jsonPrimitive?.floatOrNull,
            filter = obj["filter"]?.let { parseFilter(it) },
            layout = obj["layout"]?.jsonObject?.mapValues { (_, v) -> parsePropertyValue(v) } ?: emptyMap(),
            paint = obj["paint"]?.jsonObject?.mapValues { (_, v) -> parsePropertyValue(v) } ?: emptyMap(),
        )
    }

    private fun parseLayerType(type: String): LayerType = when (type) {
        "background" -> LayerType.Background
        "circle" -> LayerType.Circle
        "fill" -> LayerType.Fill
        "fill-extrusion" -> LayerType.FillExtrusion
        "heatmap" -> LayerType.Heatmap
        "hillshade" -> LayerType.Hillshade
        "line" -> LayerType.Line
        "raster" -> LayerType.Raster
        "symbol" -> LayerType.Symbol
        "color-relief" -> LayerType.ColorRelief
        "location-indicator" -> LayerType.LocationIndicator
        else -> LayerType.Fill
    }

    private fun parsePropertyValue(element: JsonElement): PropertyValue {
        if (element is JsonArray) {
            // expression: ["interpolate", ...] or ["get", ...]
            val expr = ExpressionParser().parse(element)
            if (expr != null) {
                return PropertyValue.Expression(expr)
            }
        }
        return PropertyValue.Constant(primitiveValue(element))
    }

    private fun primitiveValue(element: JsonElement): Any? {
        if (element is JsonPrimitive) {
            element.booleanOrNull?.let { return it }
            element.longOrNull?.let { return it }
            element.doubleOrNull?.let { return it }
            return element.content
        }
        return null
    }

    private fun parseFilter(element: JsonElement): FilterSpec {
        val arr = element.jsonArray
        val op = arr.firstOrNull()?.jsonPrimitive?.content ?: return FilterSpec.Has("")
        return when (op) {
            "==" -> FilterSpec.Equals(arr[1].jsonPrimitive.content, primitiveValue(arr[2]))
            "!=" -> FilterSpec.NotEquals(arr[1].jsonPrimitive.content, primitiveValue(arr[2]))
            "in" -> FilterSpec.In(arr[1].jsonPrimitive.content, arr.drop(2).map { primitiveValue(it) })
            "has" -> FilterSpec.Has(arr[1].jsonPrimitive.content)
            "!has" -> FilterSpec.NoneOf(listOf(FilterSpec.Has(arr[1].jsonPrimitive.content)))
            "all" -> FilterSpec.AllOf(arr.drop(1).map { parseFilter(it) })
            "any" -> FilterSpec.AnyOf(arr.drop(1).map { parseFilter(it) })
            "none" -> FilterSpec.NoneOf(arr.drop(1).map { parseFilter(it) })
            else -> FilterSpec.Has("")
        }
    }
    private fun parseLight(element: JsonElement): LightSpec {
        val obj = element.jsonObject
        val pos = obj["position"]?.jsonArray
        return LightSpec(
            color = obj["color"]?.jsonPrimitive?.content ?: "#ffffff",
            intensity = obj["intensity"]?.jsonPrimitive?.doubleOrNull ?: 0.4,
            anchor = obj["anchor"]?.jsonPrimitive?.content ?: "map",
            position = if (pos != null && pos.size >= 3) {
                Triple(
                    pos[0].jsonPrimitive.doubleOrNull ?: 1.15,
                    pos[1].jsonPrimitive.doubleOrNull ?: 210.0,
                    pos[2].jsonPrimitive.doubleOrNull ?: 30.0,
                )
            } else {
                Triple(1.15, 210.0, 30.0)
            },
        )
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this is JsonPrimitive && !isString) null else content
}
