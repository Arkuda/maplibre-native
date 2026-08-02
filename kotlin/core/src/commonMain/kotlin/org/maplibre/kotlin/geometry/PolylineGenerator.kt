package org.maplibre.kotlin.geometry

import org.maplibre.kotlin.math.Point2D
import org.maplibre.kotlin.math.Vec2
import org.maplibre.kotlin.math.VecMath
import org.maplibre.kotlin.style.LineCapType
import org.maplibre.kotlin.style.LineJoinType
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.util.EXTENT
import org.maplibre.kotlin.util.TILE_SIZE
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Port of mbgl::gfx::PolylineGenerator: turns a polyline (list of points in
 * tile units) into a triangle strip mesh with the given joins and caps.
 *
 * The output is a list of layout vertices (position + extrusion + flags) and
 * triangle indices. The renderer later scales the extrusion by half the line
 * width in the vertex shader — this generator only produces the unit-width
 * skeleton geometry.
 */

/** Options controlling line generation. Mirrors PolylineGeneratorOptions. */
class PolylineGeneratorOptions(
    val type: FeatureType = FeatureType.LINESTRING,
    val joinType: LineJoinType = LineJoinType.Miter,
    val miterLimit: Float = 2.0f,
    val beginCap: LineCapType = LineCapType.Butt,
    val endCap: LineCapType = LineCapType.Butt,
    val roundLimit: Float = 1.0f,
    val overscaling: Int = 1,
)

/** One output vertex of the line skeleton. */
class LineVertex(
    val x: Int,
    val y: Int,
    val extrudeX: Double,
    val extrudeY: Double,
    val isRound: Boolean,
    val isUp: Boolean,
    val dir: Int,
    val linesofar: Int,
)

/** A triangle (vertex indices relative to the current line start). */
private class TriangleElement(val a: Int, val b: Int, val c: Int)

private const val COS_HALF_SHARP_CORNER = 0.7933533402912352 // cos(37.5°)
private const val SHARP_CORNER_OFFSET = 15.0f
private const val DEG_PER_TRIANGLE = 20.0f
private const val LINE_DISTANCE_BUFFER_BITS = 14
private const val LINE_DISTANCE_SCALE = 1.0 / 2.0
private const val MAX_LINE_DISTANCE = ((1 shl LINE_DISTANCE_BUFFER_BITS) / LINE_DISTANCE_SCALE).toFloat()

/**
 * Generates the triangulated skeleton of a line.
 */
class PolylineGenerator(
    private val vertices: MutableList<LineVertex>,
    private val indices: MutableList<Int>,
) {

    private var e1: Int = -1
    private var e2: Int = -1
    private var e3: Int = -1

    fun generate(coordinates: List<TilePoint>, options: PolylineGeneratorOptions) {
        val len0 = coordinates.size
        var len = len0
        while (len >= 2 && coordinates[len - 1] == coordinates[len - 2]) len--
        var first = 0
        while (first + 1 < len && coordinates[first] == coordinates[first + 1]) first++

        val minLen = if (options.type == FeatureType.POLYGON) 3 else 2
        if (len < minLen) return

        val joinType = options.joinType
        val miterLimit = if (joinType == LineJoinType.Bevel) 1.05f else options.miterLimit
        val overscaling = options.overscaling

        val sharpCornerOffset = when {
            overscaling == 0 -> SHARP_CORNER_OFFSET * (EXTENT / TILE_SIZE)
            overscaling <= 16 -> SHARP_CORNER_OFFSET * (EXTENT / (TILE_SIZE * overscaling))
            else -> 0.0
        }

        val firstCoordinate = coordinates[first]
        val beginCap = options.beginCap
        val endCap = if (options.type == FeatureType.POLYGON) LineCapType.Butt else options.endCap

        var distance = 0.0
        var startOfLine = true
        var currentCoordinate: TilePoint? = null
        var prevCoordinate: TilePoint? = null
        var nextCoordinate: TilePoint? = null
        var prevNormal: Vec2? = null
        var nextNormal: Vec2? = null

        e1 = -1
        e2 = -1
        e3 = -1

        if (options.type == FeatureType.POLYGON) {
            currentCoordinate = coordinates[len - 2]
            nextNormal = VecMath.perp(
                VecMath.unit(firstCoordinate.toVec2() - currentCoordinate!!.toVec2()),
            )
        }

        val startVertex = vertices.size
        val triangleStore = mutableListOf<TriangleElement>()

        for (i in first until len) {
            nextCoordinate = when {
                options.type == FeatureType.POLYGON && i == len - 1 -> coordinates[first + 1]
                i + 1 < len -> coordinates[i + 1]
                else -> null
            }

            // skip duplicate consecutive vertices
            if (nextCoordinate != null && coordinates[i] == nextCoordinate) continue

            if (nextNormal != null) prevNormal = nextNormal
            if (currentCoordinate != null) prevCoordinate = currentCoordinate

            currentCoordinate = coordinates[i]

            nextNormal = if (nextCoordinate != null) {
                VecMath.perp(VecMath.unit(nextCoordinate.toVec2() - currentCoordinate.toVec2()))
            } else {
                prevNormal
            }

            if (prevNormal == null) prevNormal = nextNormal

            var joinNormal = prevNormal!! + nextNormal!!
            if (joinNormal.x != 0.0 || joinNormal.y != 0.0) {
                joinNormal = VecMath.unit(joinNormal)
            }

            val cosAngle = prevNormal!!.x * nextNormal!!.x + prevNormal!!.y * nextNormal!!.y
            val cosHalfAngle = joinNormal.x * nextNormal!!.x + joinNormal.y * nextNormal!!.y

            val miterLength = if (cosHalfAngle != 0.0) 1.0 / cosHalfAngle else Double.POSITIVE_INFINITY

            val approxAngle = 2.0 * sqrt(2.0 - 2.0 * cosHalfAngle)

            val isSharpCorner = cosHalfAngle < COS_HALF_SHARP_CORNER && prevCoordinate != null && nextCoordinate != null

            if (isSharpCorner && i > first) {
                val prevSegmentLength = VecMath.dist(currentCoordinate.toVec2(), prevCoordinate!!.toVec2())
                if (prevSegmentLength > 2.0 * sharpCornerOffset) {
                    val offset = VecMath.round(
                        (currentCoordinate.toVec2() - prevCoordinate!!.toVec2()) *
                            (sharpCornerOffset / prevSegmentLength),
                    )
                    val newPrevVertex = TilePoint(currentCoordinate.x - offset.x, currentCoordinate.y - offset.y)
                    distance += VecMath.dist(newPrevVertex.toVec2(), prevCoordinate!!.toVec2())
                    addCurrentVertex(newPrevVertex, distance, prevNormal!!, 0.0, 0.0, false, startVertex, triangleStore)
                    prevCoordinate = newPrevVertex
                }
            }

            val middleVertex = prevCoordinate != null && nextCoordinate != null
            var currentJoin = joinType
            val currentCap = if (nextCoordinate != null) beginCap else endCap

            if (middleVertex) {
                if (currentJoin == LineJoinType.Round) {
                    if (miterLength < options.roundLimit) {
                        currentJoin = LineJoinType.Miter
                    } else if (miterLength <= 2) {
                        currentJoin = LineJoinType.FakeRound
                    }
                }

                if (currentJoin == LineJoinType.Miter && miterLength > miterLimit) {
                    currentJoin = LineJoinType.Bevel
                }

                if (currentJoin == LineJoinType.Bevel) {
                    if (miterLength > 2) {
                        currentJoin = LineJoinType.FlipBevel
                    }
                    if (miterLength < miterLimit) {
                        currentJoin = LineJoinType.Miter
                    }
                }
            }

            if (prevCoordinate != null) {
                distance += VecMath.dist(currentCoordinate.toVec2(), prevCoordinate!!.toVec2())
            }

            when {
                middleVertex && currentJoin == LineJoinType.Miter -> {
                    joinNormal = joinNormal * miterLength
                    addCurrentVertex(currentCoordinate, distance, joinNormal, 0.0, 0.0, false, startVertex, triangleStore)
                }

                middleVertex && currentJoin == LineJoinType.FlipBevel -> {
                    if (miterLength > 100) {
                        joinNormal = nextNormal!! * -1.0
                    } else {
                        val direction = if (prevNormal!!.x * nextNormal!!.y - prevNormal!!.y * nextNormal!!.x > 0) -1.0 else 1.0
                        val bevelLength = miterLength * VecMath.mag(prevNormal!! + nextNormal!!) /
                            VecMath.mag(prevNormal!! - nextNormal!!)
                        joinNormal = VecMath.perp(joinNormal) * bevelLength * direction
                    }

                    addCurrentVertex(currentCoordinate, distance, joinNormal, 0.0, 0.0, false, startVertex, triangleStore)
                    addCurrentVertex(currentCoordinate, distance, joinNormal * -1.0, 0.0, 0.0, false, startVertex, triangleStore)
                }

                middleVertex && (currentJoin == LineJoinType.Bevel || currentJoin == LineJoinType.FakeRound) -> {
                    val lineTurnsLeft = prevNormal!!.x * nextNormal!!.y - prevNormal!!.y * nextNormal!!.x > 0
                    val offset = -sqrt(miterLength * miterLength - 1).toFloat()
                    val offsetA: Float
                    val offsetB: Float
                    if (lineTurnsLeft) {
                        offsetB = 0f
                        offsetA = offset
                    } else {
                        offsetA = 0f
                        offsetB = offset
                    }

                    if (!startOfLine) {
                        addCurrentVertex(
                            currentCoordinate, distance, prevNormal!!,
                            offsetA.toDouble(), offsetB.toDouble(), false, startVertex, triangleStore,
                        )
                    }

                    if (currentJoin == LineJoinType.FakeRound) {
                        val n = ((approxAngle * 180.0 / PI) / DEG_PER_TRIANGLE).roundToInt().coerceAtLeast(1)
                        for (m in 1 until n) {
                            var t = m.toDouble() / n
                            if (t != 0.5) {
                                val t2 = t - 0.5
                                val A = 1.0904 + cosAngle * (-3.2452 + cosAngle * (3.55645 - cosAngle * 1.43519))
                                val B = 0.848013 + cosAngle * (-1.06021 + cosAngle * 0.215638)
                                t = t + t * t2 * (t - 1) * (A * t2 * t2 + B)
                            }
                            val approxFractionalNormal = VecMath.unit(prevNormal!! * (1.0 - t) + nextNormal!! * t)
                            addPieSliceVertex(currentCoordinate, distance, approxFractionalNormal, lineTurnsLeft, startVertex, triangleStore)
                        }
                    }

                    if (nextCoordinate != null) {
                        addCurrentVertex(
                            currentCoordinate, distance, nextNormal!!,
                            -offsetA.toDouble(), -offsetB.toDouble(), false, startVertex, triangleStore,
                        )
                    }
                }

                !middleVertex && currentCap == LineCapType.Butt -> {
                    if (!startOfLine) {
                        addCurrentVertex(currentCoordinate, distance, prevNormal!!, 0.0, 0.0, false, startVertex, triangleStore)
                    }
                    if (nextCoordinate != null) {
                        addCurrentVertex(currentCoordinate, distance, nextNormal!!, 0.0, 0.0, false, startVertex, triangleStore)
                    }
                }

                !middleVertex && currentCap == LineCapType.Square -> {
                    if (!startOfLine) {
                        addCurrentVertex(currentCoordinate, distance, prevNormal!!, 1.0, 1.0, false, startVertex, triangleStore)
                        e1 = -1
                        e2 = -1
                    }
                    if (nextCoordinate != null) {
                        addCurrentVertex(currentCoordinate, distance, nextNormal!!, -1.0, -1.0, false, startVertex, triangleStore)
                    }
                }

                (middleVertex && currentJoin == LineJoinType.Round) || (!middleVertex && currentCap == LineCapType.Round) -> {
                    if (!startOfLine) {
                        addCurrentVertex(currentCoordinate, distance, prevNormal!!, 0.0, 0.0, false, startVertex, triangleStore)
                        addCurrentVertex(currentCoordinate, distance, prevNormal!!, 1.0, 1.0, true, startVertex, triangleStore)
                        e1 = -1
                        e2 = -1
                    }
                    if (nextCoordinate != null) {
                        addCurrentVertex(currentCoordinate, distance, nextNormal!!, -1.0, -1.0, true, startVertex, triangleStore)
                        addCurrentVertex(currentCoordinate, distance, nextNormal!!, 0.0, 0.0, false, startVertex, triangleStore)
                    }
                }
            }

            if (isSharpCorner && i < len - 1) {
                val nextSegmentLength = VecMath.dist(currentCoordinate.toVec2(), nextCoordinate!!.toVec2())
                if (nextSegmentLength > 2.0 * sharpCornerOffset) {
                    val offset = VecMath.round(
                        (nextCoordinate!!.toVec2() - currentCoordinate.toVec2()) *
                            (sharpCornerOffset / nextSegmentLength),
                    )
                    val newCurrentVertex = TilePoint(currentCoordinate.x + offset.x, currentCoordinate.y + offset.y)
                    distance += VecMath.dist(newCurrentVertex.toVec2(), currentCoordinate.toVec2())
                    addCurrentVertex(newCurrentVertex, distance, nextNormal!!, 0.0, 0.0, false, startVertex, triangleStore)
                    currentCoordinate = newCurrentVertex
                }
            }

            startOfLine = false
        }

        // flush triangles into the shared index buffer
        for (t in triangleStore) {
            indices.add(startVertex + t.a)
            indices.add(startVertex + t.b)
            indices.add(startVertex + t.c)
        }
    }

    private fun addCurrentVertex(
        currentCoordinate: TilePoint,
        distance: Double,
        normal: Vec2,
        endLeft: Double,
        endRight: Double,
        round: Boolean,
        startVertex: Int,
        triangleStore: MutableList<TriangleElement>,
    ) {
        var extrude = normal
        val scaledDistance = distance

        if (endLeft != 0.0) extrude = extrude - (VecMath.perp(normal) * endLeft)
        vertices.add(
            LineVertex(
                currentCoordinate.x.toInt(),
                currentCoordinate.y.toInt(),
                extrude.x, extrude.y,
                round, false,
                endLeft.toInt(),
                (scaledDistance * LINE_DISTANCE_SCALE).toInt(),
            ),
        )
        e3 = vertices.size - 1 - startVertex
        if (e1 >= 0 && e2 >= 0) triangleStore.add(TriangleElement(e1, e2, e3))
        e1 = e2
        e2 = e3

        extrude = normal * -1.0
        if (endRight != 0.0) extrude = extrude - (VecMath.perp(normal) * endRight)
        vertices.add(
            LineVertex(
                currentCoordinate.x.toInt(),
                currentCoordinate.y.toInt(),
                extrude.x, extrude.y,
                round, true,
                (-endRight).toInt(),
                (scaledDistance * LINE_DISTANCE_SCALE).toInt(),
            ),
        )
        e3 = vertices.size - 1 - startVertex
        if (e1 >= 0 && e2 >= 0) triangleStore.add(TriangleElement(e1, e2, e3))
        e1 = e2
        e2 = e3

        // reset line distance when it gets too large to fit the buffer
        if (distance > MAX_LINE_DISTANCE / 2.0f) {
            addCurrentVertex(
                currentCoordinate, 0.0, normal, endLeft, endRight, round, startVertex, triangleStore,
            )
        }
    }

    private fun addPieSliceVertex(
        currentVertex: TilePoint,
        distance: Double,
        extrude: Vec2,
        lineTurnsLeft: Boolean,
        startVertex: Int,
        triangleStore: MutableList<TriangleElement>,
    ) {
        val flippedExtrude = extrude * (if (lineTurnsLeft) -1.0 else 1.0)
        vertices.add(
            LineVertex(
                currentVertex.x.toInt(),
                currentVertex.y.toInt(),
                flippedExtrude.x, flippedExtrude.y,
                false, lineTurnsLeft,
                0,
                (distance * LINE_DISTANCE_SCALE).toInt(),
            ),
        )
        e3 = vertices.size - 1 - startVertex
        if (e1 >= 0 && e2 >= 0) triangleStore.add(TriangleElement(e1, e2, e3))

        if (lineTurnsLeft) {
            e2 = e3
        } else {
            e1 = e3
        }
    }

    private fun TilePoint.toVec2(): Vec2 = Point2D(x, y)

    private fun Vec2.toTilePoint(): TilePoint = TilePoint(x, y)
}
