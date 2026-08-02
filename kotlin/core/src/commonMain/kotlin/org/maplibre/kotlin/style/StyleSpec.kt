package org.maplibre.kotlin.style

/**
 * Typed style model — replaces the JSON-string bridge between the platform
 * layer and the engine core.
 *
 * In the original C++/Java stack the whole style travelled across JNI as a
 * JSON string (`setStyleJson(String)`), and layer properties were serialized
 * to Gson `JsonElement`s before crossing the boundary. Here the same data is
 * plain Kotlin objects: the platform constructs a [StyleSpec] directly and the
 * core consumes it without any JSON round-trip.
 */
data class StyleSpec(
    val version: Int = 8,
    val name: String? = null,
    val sources: Map<String, SourceSpec> = emptyMap(),
    val layers: List<LayerSpec> = emptyList(),
    val sprite: String? = null,
    val glyphs: String? = null,
    val light: LightSpec? = null,
)

/** Typed map source. */
sealed class SourceSpec {
    abstract val id: String
    abstract val type: String

    data class Vector(
        override val id: String,
        val url: String? = null,
        val tiles: List<String> = emptyList(),
        val minzoom: Int = 0,
        val maxzoom: Int = 22,
        val attribution: String? = null,
    ) : SourceSpec() {
        override val type: String get() = "vector"
    }

    data class Raster(
        override val id: String,
        val url: String? = null,
        val tiles: List<String> = emptyList(),
        val tileSize: Int = 512,
        val attribution: String? = null,
    ) : SourceSpec() {
        override val type: String get() = "raster"
    }

    data class GeoJson(
        override val id: String,
        /** Typed geometry, NOT a JSON string. */
        val geometry: GeoJsonGeometry? = null,
        val attribution: String? = null,
    ) : SourceSpec() {
        override val type: String get() = "geojson"
    }
}

/** Minimal typed GeoJSON geometry (Point/LineString/Polygon). */
sealed class GeoJsonGeometry {
    data class Point(val x: Double, val y: Double) : GeoJsonGeometry()
    data class LineString(val points: List<GeoJsonGeometry.Point>) : GeoJsonGeometry()
    data class Polygon(val rings: List<GeoJsonGeometry.LineString>) : GeoJsonGeometry()
}

/** Typed style layer. */
data class LayerSpec(
    val id: String,
    val type: LayerType,
    val source: String? = null,
    val sourceLayer: String? = null,
    val minzoom: Float? = null,
    val maxzoom: Float? = null,
    val filter: FilterSpec? = null,
    val layout: Map<String, PropertyValue> = emptyMap(),
    val paint: Map<String, PropertyValue> = emptyMap(),
    /** Layers below which this layer should be inserted (for addLayerBelow). */
    val below: String? = null,
)

enum class LayerType {
    Background, Circle, Fill, FillExtrusion, Heatmap, Hillshade, Line, Raster, Symbol, ColorRelief, LocationIndicator,
}

/** Typed property value: constant, or a data-driven expression tree. */
sealed class PropertyValue {
    data class Constant(val value: Any?) : PropertyValue()
    data class Expression(val steps: List<Any?>) : PropertyValue()
}

/** Typed filter: simple comparison or compound expression. */
sealed class FilterSpec {
    data class Equals(val key: String, val value: kotlin.Any?) : FilterSpec()
    data class NotEquals(val key: String, val value: kotlin.Any?) : FilterSpec()
    data class In(val key: String, val values: List<kotlin.Any?>) : FilterSpec()
    data class Has(val key: String) : FilterSpec()
    data class AllOf(val filters: List<FilterSpec>) : FilterSpec()
    data class AnyOf(val filters: List<FilterSpec>) : FilterSpec()
    data class NoneOf(val filters: List<FilterSpec>) : FilterSpec()
}

/** Typed light definition. */
data class LightSpec(
    val color: String = "#ffffff",
    val intensity: Double = 0.4,
    val anchor: String = "map",
    val position: Triple<Double, Double, Double> = Triple(1.15, 210.0, 30.0),
)
