package org.maplibre.kotlin.map

import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.SourceSpec
import org.maplibre.kotlin.style.StyleSpec
import org.maplibre.kotlin.tile.CanonicalTileID
import org.maplibre.kotlin.util.LatLng
import org.maplibre.kotlin.util.ScreenCoordinate

/**
 * The engine boundary between the platform layer and the map core.
 *
 * In the C++/Java stack every interaction crossed JNI with strings: the whole
 * style was handed over as a JSON document (`setStyleJson`), sources and
 * layers were serialized to Gson `JsonElement`s. Here the same operations are
 * plain typed calls — Kotlin objects in, Kotlin objects out. No JSON, no
 * stringly-typed protocol.
 */
interface MapEngine {
    /** Applies a fully typed style; no JSON string involved. */
    fun setStyle(style: StyleSpec)

    /** Adds a single typed source. */
    fun addSource(source: SourceSpec)

    /** Removes a source by id. Returns false when unknown. */
    fun removeSource(sourceId: String): Boolean

    /** Adds a typed layer (optionally below another layer). */
    fun addLayer(layer: LayerSpec)

    /** Removes a layer by id. Returns false when unknown. */
    fun removeLayer(layerId: String): Boolean

    /** Camera */
    fun setCamera(center: LatLng, zoom: Double, bearing: Double = 0.0, pitch: Double = 0.0)

    fun getCamera(): CameraState

    /** Converts a screen point to geographic coordinates. */
    fun screenCoordinateToLatLng(screenCoordinate: ScreenCoordinate): LatLng

    /** Converts geographic coordinates to a screen point. */
    fun latLngToScreenCoordinate(latLng: LatLng): ScreenCoordinate

    /** Tiles currently needed to cover the viewport. */
    fun visibleTiles(): List<CanonicalTileID>
}

data class CameraState(
    val center: LatLng,
    val zoom: Double,
    val bearing: Double,
    val pitch: Double,
)
