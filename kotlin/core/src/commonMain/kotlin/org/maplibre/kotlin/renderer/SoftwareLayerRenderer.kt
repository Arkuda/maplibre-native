package org.maplibre.kotlin.renderer

import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.math.Matrix4
import org.maplibre.kotlin.renderer.bucket.FillBucket
import org.maplibre.kotlin.renderer.bucket.LineBucket
import org.maplibre.kotlin.renderer.program.CircleProgram
import org.maplibre.kotlin.renderer.program.FillProgram
import org.maplibre.kotlin.renderer.program.LineProgram
import org.maplibre.kotlin.renderer.program.RasterProgram
import org.maplibre.kotlin.renderer.program.SymbolProgram
import org.maplibre.kotlin.style.layer.CircleLayer
import org.maplibre.kotlin.style.layer.FillLayer
import org.maplibre.kotlin.style.layer.LineLayer
import org.maplibre.kotlin.style.layer.RasterLayer
import org.maplibre.kotlin.style.layer.SymbolLayer
import org.maplibre.kotlin.tile.TileLayer

/**
 * CPU-side draw of a style layer: connects the layer → bucket → shader
 * pipeline so geometry can be rasterized without a GPU. This is the
 * reference implementation of the fill/line shaders — the same evaluated
 * props and matrix feed the GLES/Metal backends later.
 *
 * The renderer consumes a tile, builds the bucket for each layer, evaluates
 * the paint properties at the current zoom, and emits [DrawItem]s that a
 * rasterizer can turn into pixels.
 */
class SoftwareLayerRenderer {

    /** One ready-to-draw unit: geometry + program inputs. */
    sealed class DrawItem {
        abstract val layerId: String

        /** Full-canvas background fill. */
        class Background(
            override val layerId: String,
            val color: Color,
            val opacity: Float,
        ) : DrawItem()

        /** Fill geometry with evaluated paint. */
        class Fill(
            override val layerId: String,
            val bucket: FillBucket,
            val fillUniforms: FillProgram.Uniforms,
            val opacity: Float,
        ) : DrawItem()

        /** Line geometry with evaluated paint. */
        class Line(
            override val layerId: String,
            val bucket: LineBucket,
            val props: LineProgram.Props,
            val lineUniforms: LineProgram.Uniforms,
        ) : DrawItem()

        /** Circle geometry (point features) with evaluated paint. */
        class Circle(
            override val layerId: String,
            val bucket: org.maplibre.kotlin.renderer.bucket.CircleBucket,
            val props: CircleProgram.Props,
            val circleUniforms: CircleProgram.Uniforms,
        ) : DrawItem()

        /** Text labels (point features) with evaluated paint. */
        class Symbol(
            override val layerId: String,
            val bucket: org.maplibre.kotlin.renderer.bucket.SymbolBucket,
            val evaluated: org.maplibre.kotlin.style.layer.SymbolLayer.Evaluated,
            val symbolUniforms: SymbolProgram.Uniforms,
        ) : DrawItem()

        /** Raster tile image with color adjustments. */
        class Raster(
            override val layerId: String,
            val bucket: org.maplibre.kotlin.renderer.bucket.RasterBucket,
            val props: RasterProgram.Props,
        ) : DrawItem()
    }

    /**
     * Draws all fill/line layers against one decoded tile.
     *
     * @param layers style layers (already created from the style spec)
     * @param tile the decoded tile layer map, keyed by source layer name
     * @param zoom current zoom for paint evaluation
     * @param matrix tile-space → screen matrix
     * @param pixelRatio device pixel ratio
     * @param layerFilter optional extra predicate (id, layer) -> keep
     */
    fun render(
        layers: List<org.maplibre.kotlin.style.layer.StyleLayer>,
        tile: org.maplibre.kotlin.tile.VectorTileData,
        zoom: Float,
        matrix: Matrix4,
        pixelRatio: Float = 1.0f,
        layerFilter: ((String, org.maplibre.kotlin.style.layer.StyleLayer) -> Boolean)? = null,
        /** Raster tiles by source id (for raster layers). */
        rasterTiles: Map<String, org.maplibre.kotlin.renderer.bucket.RasterBucket> = emptyMap(),
    ): List<DrawItem> {
        val out = mutableListOf<DrawItem>()
        for (layer in layers) {
            if (!layer.isVisible(zoom)) continue
            if (layerFilter != null && !layerFilter(layer.id, layer)) continue

            // Background layers have no tile source: paint the whole canvas.
            if (layer is org.maplibre.kotlin.style.layer.BackgroundLayer) {
                val evaluated = layer.evaluate(zoom)
                if (evaluated.opacity <= 0.0) continue
                out.add(
                    DrawItem.Background(
                        layerId = layer.id,
                        color = evaluated.color,
                        opacity = evaluated.opacity.toFloat(),
                    ),
                )
                continue
            }

            // Raster layers draw a raster tile image from the source.
            if (layer is org.maplibre.kotlin.style.layer.RasterLayer) {
                val src = layer.source ?: continue
                val bucket = rasterTiles[src] ?: continue
                if (bucket.isEmpty) continue
                val evaluated = layer.evaluate(zoom)
                out.add(
                    DrawItem.Raster(
                        layerId = layer.id,
                        bucket = bucket,
                        props = RasterProgram.Props(
                            opacity = evaluated.opacity.toFloat(),
                            spinWeights = RasterProgram.spinWeights(evaluated.hueRotate),
                            saturationFactor = RasterProgram.saturationFactor(evaluated.saturation),
                            contrastFactor = RasterProgram.contrastFactor(evaluated.contrast),
                            brightnessLow = evaluated.brightnessMin.toFloat(),
                            brightnessHigh = evaluated.brightnessMax.toFloat(),
                        ),
                    ),
                )
                continue
            }

            val sourceLayerName = layer.sourceLayer
            if (sourceLayerName == null) continue
            val tileLayer = tile.getLayer(sourceLayerName) ?: continue

            when (layer) {
                is FillLayer -> {
                    val bucket = layer.buildBucket(tileLayer)
                    if (bucket.isEmpty) continue
                    val evaluated = layer.evaluate(zoom)
                    out.add(
                        DrawItem.Fill(
                            layerId = layer.id,
                            bucket = bucket,
                            fillUniforms = FillProgram.Uniforms(
                                matrix = matrix,
                                color = evaluated.color,
                                opacity = evaluated.opacity.toFloat(),
                            ),
                            opacity = evaluated.opacity.toFloat(),
                        ),
                    )
                }

                is LineLayer -> {
                    val bucket = layer.buildBucket(tileLayer)
                    if (bucket.isEmpty) continue
                    val evaluated = layer.evaluate(zoom)
                    out.add(
                        DrawItem.Line(
                            layerId = layer.id,
                            bucket = bucket,
                            props = LineProgram.Props(
                                width = evaluated.width,
                                gapWidth = evaluated.gapWidth,
                                offset = evaluated.offset,
                                blur = evaluated.blur,
                                color = evaluated.color,
                                opacity = evaluated.opacity,
                            ),
                            lineUniforms = LineProgram.Uniforms(
                                matrix = matrix,
                                devicePixelRatio = pixelRatio,
                            ),
                        ),
                    )
                }

                is CircleLayer -> {
                    val bucket = layer.buildBucket(tileLayer)
                    if (bucket.isEmpty) continue
                    val evaluated = layer.evaluate(zoom)
                    out.add(
                        DrawItem.Circle(
                            layerId = layer.id,
                            bucket = bucket,
                            props = CircleProgram.Props(
                                color = evaluated.color,
                                radius = evaluated.radius,
                                blur = evaluated.blur,
                                opacity = evaluated.opacity,
                                strokeColor = evaluated.strokeColor,
                                strokeWidth = evaluated.strokeWidth,
                                strokeOpacity = evaluated.strokeOpacity,
                                scaleWithMap = evaluated.scaleWithMap,
                            ),
                            circleUniforms = CircleProgram.Uniforms(
                                matrix = matrix,
                                devicePixelRatio = pixelRatio,
                            ),
                        ),
                    )
                }

                is SymbolLayer -> {
                    val bucket = layer.buildBucket(tileLayer)
                    if (bucket.isEmpty) continue
                    val evaluated = layer.evaluate(zoom)
                    out.add(
                        DrawItem.Symbol(
                            layerId = layer.id,
                            bucket = bucket,
                            evaluated = evaluated,
                            symbolUniforms = SymbolProgram.Uniforms(
                                matrix = matrix,
                                devicePixelRatio = pixelRatio,
                            ),
                        ),
                    )
                }

                else -> Unit // unported layer types are skipped
            }
        }
        return out
    }

    // ---- rasterization helpers -------------------------------------------

    /**
     * Rasterizes a background draw item: fills the whole framebuffer.
     * @return premultiplied RGBA bytes, row-major, top-down
     */
    fun rasterizeBackground(
        item: DrawItem.Background,
        width: Int,
        height: Int,
    ): ByteArray {
        val pixels = ByteArray(width * height * 4)
        val r = (item.color.r * 255 * item.opacity).toInt().coerceIn(0, 255)
        val g = (item.color.g * 255 * item.opacity).toInt().coerceIn(0, 255)
        val b = (item.color.b * 255 * item.opacity).toInt().coerceIn(0, 255)
        val a = (item.color.a * 255 * item.opacity).toInt().coerceIn(0, 255)
        for (i in 0 until pixels.size step 4) {
            pixels[i] = r.toByte()
            pixels[i + 1] = g.toByte()
            pixels[i + 2] = b.toByte()
            pixels[i + 3] = a.toByte()
        }
        return pixels
    }

    /**
     * Rasterizes a fill draw item into an RGBA pixel buffer.
     *
     * @param width/height framebuffer size in pixels
     * @return premultiplied RGBA bytes, row-major, top-down
     */
    fun rasterizeFill(
        item: DrawItem.Fill,
        width: Int,
        height: Int,
    ): ByteArray {
        val pixels = ByteArray(width * height * 4)
        val frag = FillProgram.fragment(item.fillUniforms)
        val fb = framebuffer(width, height)

        for (i in 0 until item.bucket.indices.size step 3) {
            val ia = item.bucket.indices[i]
            val ib = item.bucket.indices[i + 1]
            val ic = item.bucket.indices[i + 2]

            val a = item.bucket.vertices[ia]
            val b = item.bucket.vertices[ib]
            val c = item.bucket.vertices[ic]

            val va = FillProgram.vertex(a.x.toFloat(), a.y.toFloat(), item.fillUniforms)
            val vb = FillProgram.vertex(b.x.toFloat(), b.y.toFloat(), item.fillUniforms)
            val vc = FillProgram.vertex(c.x.toFloat(), c.y.toFloat(), item.fillUniforms)

            fillTriangle(fb, va, vb, vc, frag, pixels)
        }
        return pixels
    }

    /**
     * Rasterizes a line draw item into an RGBA pixel buffer. For each line
     * triangle the three corners are expanded through [LineProgram.vertex]
     * and shaded with [LineProgram.fragment].
     */
    fun rasterizeLine(
        item: DrawItem.Line,
        width: Int,
        height: Int,
    ): ByteArray {
        val pixels = ByteArray(width * height * 4)
        val fb = framebuffer(width, height)
        val uniforms = item.lineUniforms
        val props = item.props

        // Map bucket vertices once, then index into them.
        val expanded = item.bucket.vertices.map { v ->
            LineProgram.vertex(
                LineProgram.VertexInput(
                    x = v.x,
                    y = v.y,
                    extrudeX = v.extrudeX,
                    extrudeY = v.extrudeY,
                    isRound = v.isRound,
                    isUp = v.isUp,
                    direction = v.dir,
                    linesofar = v.linesofar.toDouble(),
                ),
                props,
                uniforms,
            )
        }

        for (i in 0 until item.bucket.indices.size step 3) {
            val ia = item.bucket.indices[i]
            val ib = item.bucket.indices[i + 1]
            val ic = item.bucket.indices[i + 2]

            val va = expanded[ia]
            val vb = expanded[ib]
            val vc = expanded[ic]

            // Per-corner fragment shading: sample at the barycenter of each
            // triangle and shade with the line distance field.
            val alpha = lineTriangleAlpha(va, vb, vc, props, uniforms)
            if (alpha <= 0.0) continue
            val color = LineProgram.fragmentColor(alpha, props)

            fillTriangle(fb, va, vb, vc, color, pixels)
        }
        return pixels
    }

    /**
     * Rasterizes a circle draw item into an RGBA pixel buffer. Each point
     * feature projects to a screen-space circle; pixels are shaded with the
     * circle fragment shader distance field.
     */
    fun rasterizeCircle(
        item: DrawItem.Circle,
        width: Int,
        height: Int,
    ): ByteArray {
        val pixels = ByteArray(width * height * 4)
        val fb = framebuffer(width, height)
        val uniforms = item.circleUniforms
        val props = item.props

        val radiusOuter = props.radius + props.strokeWidth
        // circle-radius is specified in pixels; circle-pitch-scale only
        // changes behaviour when the map is pitched (pitch == 0 here), so the
        // screen radius is always radius / DPR.
        val radiusPx = radiusOuter / uniforms.devicePixelRatio
        if (radiusPx <= 0.0) return pixels
        val rPx = radiusPx.toInt().coerceAtLeast(1)

        for (i in 0 until item.bucket.vertices.size) {
            val v = item.bucket.vertices[i]
            // each point appears 4x (quad corners); only draw once per point
            if (v.extrudeX != -1.0 || v.extrudeY != -1.0) continue

            val proj = CircleProgram.vertex(v.x.toFloat(), v.y.toFloat(), uniforms)
            val cx = screenX(proj.clip, fb.width)
            val cy = screenY(proj.clip, fb.height)

            val minX = maxOf(0, cx - rPx)
            val maxX = minOf(fb.width - 1, cx + rPx)
            val minY = maxOf(0, cy - rPx)
            val maxY = minOf(fb.height - 1, cy + rPx)

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val dx = (x - cx).toDouble()
                    val dy = (y - cy).toDouble()
                    // normalized distance: 0 = center, 1 = outer edge
                    val d = kotlin.math.sqrt(dx * dx + dy * dy) / radiusPx
                    val color = CircleProgram.fragment(d, props, uniforms)
                    if (color.a <= 0.0f) continue
                    val idx = (y * fb.width + x) * 4
                    pixels[idx] = (color.r * 255).toInt().coerceIn(0, 255).toByte()
                    pixels[idx + 1] = (color.g * 255).toInt().coerceIn(0, 255).toByte()
                    pixels[idx + 2] = (color.b * 255).toInt().coerceIn(0, 255).toByte()
                    pixels[idx + 3] = (color.a * 255).toInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return pixels
    }

    /**
     * Rasterizes a symbol draw item into an RGBA pixel buffer. Text is drawn
     * glyph-by-glyph from the embedded bitmap font at the evaluated size.
     */
    fun rasterizeSymbol(
        item: DrawItem.Symbol,
        width: Int,
        height: Int,
    ): ByteArray {
        val pixels = ByteArray(width * height * 4)
        val fb = framebuffer(width, height)
        val uniforms = item.symbolUniforms
        val ev = item.evaluated

        val scale = (ev.size / 12.0).toFloat() // bitmap font is 12px cap height
        val charW = (org.maplibre.kotlin.util.BitmapFont.ADVANCE * scale).toInt().coerceAtLeast(1)
        val charH = (org.maplibre.kotlin.util.BitmapFont.GLYPH_HEIGHT * scale).toInt().coerceAtLeast(1)

        for (instance in item.bucket.instances) {
            val text = instance.text ?: continue

            val proj = SymbolProgram.vertex(instance.x.toFloat(), instance.y.toFloat(), uniforms)
            val cx = screenX(proj.clip, fb.width)
            val cy = screenY(proj.clip, fb.height)

            val textW = text.length * charW
            // anchor: center by default; anchorX/Y are fractions of the box
            val x0 = (cx - (textW * ev.anchorX).toInt() + (ev.offsetX * scale).toInt())
            val y0 = (cy - (charH * ev.anchorY).toInt())

            var gx = x0
            for (ch in text) {
                if (!org.maplibre.kotlin.util.BitmapFont.isValid(ch)) {
                    gx += charW
                    continue
                }
                val glyph = org.maplibre.kotlin.util.BitmapFont.glyph(ch)
                for (row in 0 until org.maplibre.kotlin.util.BitmapFont.GLYPH_HEIGHT) {
                    val bits = glyph[row]
                    val py = y0 + (row * scale).toInt()
                    if (py < 0 || py >= fb.height) continue
                    for (col in 0 until org.maplibre.kotlin.util.BitmapFont.GLYPH_WIDTH) {
                        if (bits and (1 shl col) == 0) continue
                        val px = gx + (col * scale).toInt()
                        if (px < 0 || px >= fb.width) continue
                        val idx = (py * fb.width + px) * 4
                        pixels[idx] = (ev.color.r * 255 * ev.opacity).toInt().coerceIn(0, 255).toByte()
                        pixels[idx + 1] = (ev.color.g * 255 * ev.opacity).toInt().coerceIn(0, 255).toByte()
                        pixels[idx + 2] = (ev.color.b * 255 * ev.opacity).toInt().coerceIn(0, 255).toByte()
                        pixels[idx + 3] = (ev.opacity * 255).toInt().coerceIn(0, 255).toByte()
                    }
                }
                gx += charW
            }
        }
        return pixels
    }

    private fun lineTriangleAlpha(
        a: LineProgram.VertexOutput,
        b: LineProgram.VertexOutput,
        c: LineProgram.VertexOutput,
        props: LineProgram.Props,
        uniforms: LineProgram.Uniforms,
    ): Double {
        // Average the varyings at the triangle centroid.
        val nx = (a.normalX + b.normalX + c.normalX) / 3.0
        val ny = (a.normalY + b.normalY + c.normalY) / 3.0
        val s = (a.width2S + b.width2S + c.width2S) / 3.0
        val t = (a.width2T + b.width2T + c.width2T) / 3.0
        val g = (a.gammaScale + b.gammaScale + c.gammaScale) / 3.0
        return LineProgram.fragment(nx, ny, s, t, g, props, uniforms)
    }

    // ---- software triangle rasterizer ------------------------------------

    private class Framebuffer(val width: Int, val height: Int) {
        val depth = FloatArray(width * height) { Float.NEGATIVE_INFINITY }
        val pixels = ByteArray(width * height * 4)
    }

    private fun framebuffer(width: Int, height: Int): Framebuffer = Framebuffer(width, height)

    private fun fillTriangle(
        fb: Framebuffer,
        a: LineProgram.VertexOutput,
        b: LineProgram.VertexOutput,
        c: LineProgram.VertexOutput,
        color: Color,
        out: ByteArray,
    ) {
        val x1 = screenX(a.clip, fb.width)
        val y1 = screenY(a.clip, fb.height)
        val x2 = screenX(b.clip, fb.width)
        val y2 = screenY(b.clip, fb.height)
        val x3 = screenX(c.clip, fb.width)
        val y3 = screenY(c.clip, fb.height)

        val minX = maxOf(0, minOf(x1, x2, x3))
        val maxX = minOf(fb.width - 1, maxOf(x1, x2, x3))
        val minY = maxOf(0, minOf(y1, y2, y3))
        val maxY = minOf(fb.height - 1, maxOf(y1, y2, y3))

        val area = edge(x1, y1, x2, y2, x3, y3)
        if (area == 0) return

        val r = (color.r * 255).toInt().coerceIn(0, 255)
        val g = (color.g * 255).toInt().coerceIn(0, 255)
        val b = (color.b * 255).toInt().coerceIn(0, 255)
        val a = (color.a * 255).toInt().coerceIn(0, 255)
        // premultiplied: alpha applied to rgb
        val pr = (r * color.a).toInt().coerceIn(0, 255)
        val pg = (g * color.a).toInt().coerceIn(0, 255)
        val pb = (b * color.a).toInt().coerceIn(0, 255)

        // normalize winding: the screen-space y flip can reverse triangle order
        var ax = x1; var ay = y1
        var bx = x2; var by = y2
        var cx = x3; var cy = y3
        if (area < 0) {
            val tx = bx; val ty = by
            bx = cx; by = cy
            cx = tx; cy = ty
        }

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val w0 = edge(bx, by, cx, cy, x, y)
                val w1 = edge(cx, cy, ax, ay, x, y)
                val w2 = edge(ax, ay, bx, by, x, y)
                if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                    val idx = (y * fb.width + x) * 4
                    // simple painter's overwrite (no blending between items)
                    out[idx] = pr.toByte()
                    out[idx + 1] = pg.toByte()
                    out[idx + 2] = pb.toByte()
                    out[idx + 3] = a.toByte()
                }
            }
        }
    }

    private fun fillTriangle(
        fb: Framebuffer,
        a: FillProgram.VertexOutput,
        b: FillProgram.VertexOutput,
        c: FillProgram.VertexOutput,
        color: Color,
        out: ByteArray,
    ) {
        val x1 = screenX(a.clip, fb.width)
        val y1 = screenY(a.clip, fb.height)
        val x2 = screenX(b.clip, fb.width)
        val y2 = screenY(b.clip, fb.height)
        val x3 = screenX(c.clip, fb.width)
        val y3 = screenY(c.clip, fb.height)
        fillTriangleScreen(out, fb, x1, y1, x2, y2, x3, y3, color)
    }

    private fun fillTriangleScreen(
        out: ByteArray,
        fb: Framebuffer,
        x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int,
        color: Color,
    ) {
        val minX = maxOf(0, minOf(x1, x2, x3))
        val maxX = minOf(fb.width - 1, maxOf(x1, x2, x3))
        val minY = maxOf(0, minOf(y1, y2, y3))
        val maxY = minOf(fb.height - 1, maxOf(y1, y2, y3))

        val area = edge(x1, y1, x2, y2, x3, y3)
        if (area == 0) return

        val pr = (color.r * 255 * color.a).toInt().coerceIn(0, 255)
        val pg = (color.g * 255 * color.a).toInt().coerceIn(0, 255)
        val pb = (color.b * 255 * color.a).toInt().coerceIn(0, 255)
        val pa = (color.a * 255).toInt().coerceIn(0, 255)

        // normalize winding (screen y flip can reverse triangle order)
        var ax = x1; var ay = y1
        var bx = x2; var by = y2
        var cx = x3; var cy = y3
        if (area < 0) {
            val tx = bx; val ty = by
            bx = cx; by = cy
            cx = tx; cy = ty
        }

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val w0 = edge(bx, by, cx, cy, x, y)
                val w1 = edge(cx, cy, ax, ay, x, y)
                val w2 = edge(ax, ay, bx, by, x, y)
                if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                    val idx = (y * fb.width + x) * 4
                    out[idx] = pr.toByte()
                    out[idx + 1] = pg.toByte()
                    out[idx + 2] = pb.toByte()
                    out[idx + 3] = pa.toByte()
                }
            }
        }
    }

    private fun screenX(clip: org.maplibre.kotlin.math.Vec4, width: Int): Int =
        (((clip.x / clip.w + 1.0f) / 2.0f) * width).toInt()

    private fun screenY(clip: org.maplibre.kotlin.math.Vec4, height: Int): Int =
        (((1.0f - (clip.y / clip.w + 1.0f) / 2.0f) * height)).toInt()

    private fun edge(ax: Int, ay: Int, bx: Int, by: Int, cx: Int, cy: Int): Int =
        (cx - ax) * (by - ay) - (cy - ay) * (bx - ax)
}
