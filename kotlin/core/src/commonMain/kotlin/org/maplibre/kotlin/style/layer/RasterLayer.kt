package org.maplibre.kotlin.style.layer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.LayerSpec

/**
 * Raster layer: paints a raster tile image with color adjustments.
 * Ported from mbgl::style::RasterLayer.
 */
class RasterLayer(spec: LayerSpec) : StyleLayer(spec) {

    /** Evaluated paint properties for one zoom. */
    class Evaluated(
        val opacity: Double,
        /** Hue rotation in degrees (raster-hue-rotate). */
        val hueRotate: Double,
        /** Saturation, -1..1 (raster-saturation). */
        val saturation: Double,
        /** Contrast, -1..1 (raster-contrast). */
        val contrast: Double,
        val brightnessMin: Double,
        val brightnessMax: Double,
    )

    fun evaluate(zoom: Float): Evaluated = Evaluated(
        opacity = evaluateNumber("raster-opacity", Group.Paint, zoom, null, 1.0),
        hueRotate = evaluateNumber("raster-hue-rotate", Group.Paint, zoom, null, 0.0),
        saturation = evaluateNumber("raster-saturation", Group.Paint, zoom, null, 0.0),
        contrast = evaluateNumber("raster-contrast", Group.Paint, zoom, null, 0.0),
        brightnessMin = evaluateNumber("raster-brightness-min", Group.Paint, zoom, null, 0.0),
        brightnessMax = evaluateNumber("raster-brightness-max", Group.Paint, zoom, null, 1.0),
    )

    /** Resampling: "linear" (default) or "nearest". */
    fun resampling(zoom: Float): String =
        evaluateEnum("raster-resampling", Group.Paint, zoom, "linear", mapOf("linear" to "linear", "nearest" to "nearest"))

    /** Default raster tile size (raster-tile-size is layout-only in C++). */
    fun tileSize(): Int = 512
}
