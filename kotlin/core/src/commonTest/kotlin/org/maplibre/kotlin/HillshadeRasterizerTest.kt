package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.renderer.HillshadeRasterizer
import org.maplibre.kotlin.style.layer.HillshadeMethod

class HillshadeRasterizerTest {

    private val DIM = 64

    private fun flatDem(): FloatArray = FloatArray(DIM * DIM) { 100.0f }

    /** A ridge: elevation peaks in the middle row, sloping down both ways. */
    private fun ridgeDem(): FloatArray {
        val dem = FloatArray(DIM * DIM)
        for (y in 0 until DIM) {
            for (x in 0 until DIM) {
                dem[y * DIM + x] = 100f + 300f * (1f - kotlin.math.abs(x - (DIM - 1) / 2f) / (DIM / 2f))
            }
        }
        return dem
    }

    /** Zoom/exaggeration that makes the synthetic ridge produce strong slopes. */
    private fun strongInput(method: HillshadeMethod = HillshadeMethod.STANDARD) =
        HillshadeRasterizer.Input(exaggeration = 1.0, method = method)

    private fun meanAlpha(pixels: ByteArray): Double {
        var sum = 0.0
        for (i in 3 until pixels.size step 4) sum += (pixels[i].toInt() and 0xFF)
        return sum / (pixels.size / 4)
    }

    private fun meanBrightness(pixels: ByteArray): Double {
        var sum = 0.0
        for (i in 0 until pixels.size step 4) sum += (pixels[i].toInt() and 0xFF)
        return sum / (pixels.size / 4)
    }

    private fun meanBrightnessHalf(pixels: ByteArray, left: Boolean): Double {
        var sum = 0.0
        var n = 0
        for (y in 0 until DIM) {
            for (x in 0 until DIM) {
                if ((x < DIM / 2) != left) continue
                sum += pixels[(y * DIM + x) * 4].toInt() and 0xFF
                n++
            }
        }
        return sum / n
    }

    @Test
    fun flatTerrainIsUniform() {
        val out = HillshadeRasterizer.shade(
            flatDem(), DIM, zoom = 12.0,
            HillshadeRasterizer.Input(exaggeration = 0.5),
        )
        val first = out.copyOfRange(0, 4).toList()
        for (i in 4 until out.size step 4) {
            assertEquals(first, out.copyOfRange(i, i + 4).toList(), "flat terrain must shade uniformly at $i")
        }
    }

    @Test
    fun ridgeProducesVariation() {
        val out = HillshadeRasterizer.shade(
            ridgeDem(), DIM, zoom = 16.0,
            strongInput(),
        )
        var min = 255
        var max = 0
        for (i in 0 until out.size step 4) {
            val v = out[i].toInt() and 0xFF
            if (v < min) min = v
            if (v > max) max = v
        }
        assertTrue(max - min > 20, "ridge must produce brightness variation, got $min..$max")
    }

    @Test
    fun lightDirectionFlipsShadowSide() {
        // basic method: flipping the light direction by 180 swaps the bright side
        fun render(directionDeg: Double): ByteArray = HillshadeRasterizer.shade(
            ridgeDem(), DIM, zoom = 16.0,
            HillshadeRasterizer.Input(
                exaggeration = 1.0,
                method = HillshadeMethod.BASIC,
                illuminationDirectionDeg = directionDeg,
                illuminationAltitudeDeg = 45.0,
            ),
        )

        val fromEast = render(90.0)
        val fromWest = render(270.0)

        val leftEast = meanBrightnessHalf(fromEast, left = true)
        val rightEast = meanBrightnessHalf(fromEast, left = false)
        val leftWest = meanBrightnessHalf(fromWest, left = true)
        val rightWest = meanBrightnessHalf(fromWest, left = false)

        fun brightSide(left: Double, right: Double): String = if (left > right) "left" else "right"

        val eastLitSide = brightSide(leftEast, rightEast)
        val westLitSide = brightSide(leftWest, rightWest)

        // the two renders must be meaningfully different and mirror each other
        assertTrue(kotlin.math.abs(leftEast - leftWest) > 10.0, "light flip must change shading: $leftEast vs $leftWest")
        assertNotEquals(eastLitSide, westLitSide, "flipping the light must flip the bright side")
    }

    @Test
    fun allMethodsProduceOutput() {
        val ridge = ridgeDem()
        val outputs = mutableMapOf<HillshadeMethod, ByteArray>()
        for (m in HillshadeMethod.entries) {
            val out = HillshadeRasterizer.shade(
                ridge, DIM, zoom = 16.0,
                strongInput(m),
            )
            assertTrue(meanAlpha(out) > 5.0, "$m must produce visible output")
            outputs[m] = out
        }
        // the methods differ from each other (count differing pixels)
        val standard = outputs[HillshadeMethod.STANDARD]!!
        for ((m, out) in outputs) {
            if (m == HillshadeMethod.STANDARD) continue
            var diff = 0
            for (i in 0 until out.size step 4) {
                if (kotlin.math.abs((out[i].toInt() and 0xFF) - (standard[i].toInt() and 0xFF)) > 5) diff++
            }
            assertTrue(diff > 100, "$m must differ from standard (diff px=$diff)")
        }
    }

    @Test
    fun customColorsAreUsed() {
        val red = HillshadeRasterizer.shade(
            ridgeDem(), DIM, zoom = 16.0,
            HillshadeRasterizer.Input(
                exaggeration = 1.0,
                highlightColor = Color.red(),
                shadowColor = Color.red(),
                accentColor = Color.red(),
            ),
        )
        var redChannels = 0
        for (i in 0 until red.size step 4) {
            val r = red[i].toInt() and 0xFF
            val g = red[i + 1].toInt() and 0xFF
            val b = red[i + 2].toInt() and 0xFF
            if (r > g + 20 && r > b + 20) redChannels++
        }
        assertTrue(redChannels > 100, "custom red colors must tint the output, got $redChannels red-dominant px")
    }
}
