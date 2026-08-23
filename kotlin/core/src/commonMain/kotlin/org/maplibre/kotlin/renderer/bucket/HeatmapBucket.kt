package org.maplibre.kotlin.renderer.bucket

import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer

/**
 * Heatmap bucket: accumulates weighted point contributions into a density grid.
 * Ported from mbgl::HeatmapBucket — the C++ version uses a WebGL framebuffer;
 * here we build a FloatArray density grid on CPU.
 *
 * The grid is tile-local [dimension] x [dimension] (typically 256 or 512).
 * Each point contributes a Gaussian kernel scaled by weight/radius/intensity.
 * The renderer later samples this grid via the color-ramp expression.
 */
class HeatmapBucket(
    val dimension: Int = 256,
) {
    /** Density grid, row-major, top-down. Values are in [0, ∞). */
    val density = FloatArray(dimension * dimension) { 0f }

    /**
     * Accumulates all POINT features from the tile layer into the density grid.
     *
     * @param layer tile layer containing point features
     * @param radius heatmap-radius (pixels at tile zoom)
     * @param weight heatmap-weight (per-feature multiplier)
     * @param intensity heatmap-intensity (global multiplier)
     */
    fun addLayer(
        layer: TileLayer,
        radius: Double,
        weight: Double,
        intensity: Double,
    ) {
        if (layer.features.isEmpty()) return
        val r = radius.toFloat()
        val w = (weight * intensity).toFloat()

        // Precompute Gaussian kernel lookup for this radius
        // Kernel covers [-3*r, 3*r] to capture ~99.7% of mass
        val kernelRadius = kotlin.math.ceil(3.0 * r).toInt()
        val kernel = FloatArray((2 * kernelRadius + 1) * (2 * kernelRadius + 1)) { 0f }
        val invTwoSigma2 = 1.0f / (2.0f * r * r)
        for (dy in IntRange(-kernelRadius, kernelRadius)) {
            for (dx in IntRange(-kernelRadius, kernelRadius)) {
                val dist2 = (dx * dx + dy * dy).toFloat()
                if (dist2 <= 9.0f * r * r) {
                    kernel[(dy + kernelRadius) * (2 * kernelRadius + 1) + (dx + kernelRadius)] =
                        kotlin.math.exp((-dist2 * invTwoSigma2).toDouble()).toFloat()
                }
            }
        }

        for (feature in layer.features) {
            if (feature.type != org.maplibre.kotlin.tile.FeatureType.POINT) continue
            // Extract weight from feature properties if present
            var featureWeight = 1.0f
            feature.properties["heatmap-weight"]?.let { prop ->
                if (prop is org.maplibre.kotlin.tile.TileValue.Num) featureWeight = prop.value.toFloat()
            }
            val combinedWeight = w * featureWeight

            for (ring in feature.geometry) {
                for (pt in ring) {
                    val cx = pt.x.toInt()
                    val cy = pt.y.toInt()
                    // Add kernel contribution
                    val x0 = cx - kernelRadius
                    val y0 = cy - kernelRadius
                    for (dy in 0..2 * kernelRadius) {
                        val py = y0 + dy
                        if (py < 0 || py >= dimension) continue
                        val rowBase = py * dimension
                        val kernelRowBase = dy * (2 * kernelRadius + 1)
                        val xStart = maxOf(0, x0)
                        val xEnd = minOf(dimension, x0 + 2 * kernelRadius + 1)
                        for (px in xStart until xEnd) {
                            val kx = px - x0
                            val kVal = kernel[kernelRowBase + kx]
                            if (kVal > 0f) {
                                density[rowBase + px] += kVal * combinedWeight
                            }
                        }
                    }
                }
            }
        }
    }

    /** Returns true if no density was accumulated. */
    val isEmpty: Boolean
        get() = density.all { it == 0f }

    /** Normalizes density to [0, 1] range for the color ramp. */
    fun normalize(maxDensity: Float = 1f): FloatArray {
        val maxVal = density.maxOrNull() ?: 0f
        if (maxVal <= 0f) return density
        val scale = 1f / (maxVal * maxDensity)
        return density.map { (it * scale).coerceIn(0f, 1f) }.toFloatArray()
    }
}