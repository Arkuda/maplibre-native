package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.maplibre.kotlin.geometry.LineVertex
import org.maplibre.kotlin.geometry.PolylineGenerator
import org.maplibre.kotlin.geometry.PolylineGeneratorOptions
import org.maplibre.kotlin.style.LineCapType
import org.maplibre.kotlin.style.LineJoinType
import org.maplibre.kotlin.tile.TilePoint

class PolylineGeneratorTest {

    private fun generate(
        points: List<TilePoint>,
        options: PolylineGeneratorOptions = PolylineGeneratorOptions(),
    ): Pair<List<LineVertex>, List<Int>> {
        val vertices = mutableListOf<LineVertex>()
        val indices = mutableListOf<Int>()
        PolylineGenerator(vertices, indices).generate(points, options)
        return vertices to indices
    }

    @Test
    fun straightLineProducesTwoVerticesPerPoint() {
        val (v, idx) = generate(
            listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 0.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Miter, beginCap = LineCapType.Butt, endCap = LineCapType.Butt),
        )
        // butt caps, no middle vertex: 2 vertices per point = 4
        assertEquals(4, v.size)
        // 2 triangles per segment quad
        assertEquals(6, idx.size)
        // all indices in range
        for (i in idx) assertTrue(i in 0 until v.size)
    }

    @Test
    fun squareCapsExtrudeAlongLine() {
        val (v, _) = generate(
            listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 0.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Miter, beginCap = LineCapType.Square, endCap = LineCapType.Square),
        )
        // square caps: 2 vertices at start (extruded back), 2 at end (extruded forward)
        assertEquals(4, v.size)
        // start cap vertices have dir = -1, end cap vertices have dir = 1
        val dirs = v.map { it.dir }.toSet()
        assertEquals(setOf(-1, 1), dirs)
    }

    @Test
    fun roundCapsProduceRoundFlag() {
        val (v, _) = generate(
            listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 0.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Miter, beginCap = LineCapType.Round, endCap = LineCapType.Round),
        )
        // 4 base + 2 round cap vertices (one per end)
        assertTrue(v.size >= 6)
        assertTrue(v.any { it.isRound })
    }

    @Test
    fun middleVertexProducesJoinVertices() {
        val (v, idx) = generate(
            listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 0.0), TilePoint(200.0, 0.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Miter, beginCap = LineCapType.Butt, endCap = LineCapType.Butt),
        )
        // 3 points, straight line, miter join: 2 per point = 6 vertices
        assertEquals(6, v.size)
        assertEquals(12, idx.size) // two quads = 4 triangles
    }

    @Test
    fun duplicatePointsAreSkipped() {
        val (v, _) = generate(
            listOf(TilePoint(0.0, 0.0), TilePoint(0.0, 0.0), TilePoint(100.0, 0.0), TilePoint(100.0, 0.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Miter, beginCap = LineCapType.Butt, endCap = LineCapType.Butt),
        )
        // duplicates collapsed: same as 2-point line
        assertEquals(4, v.size)
    }

    @Test
    fun singlePointProducesNothing() {
        val (v, idx) = generate(
            listOf(TilePoint(5.0, 5.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Miter, beginCap = LineCapType.Butt, endCap = LineCapType.Butt),
        )
        assertTrue(v.isEmpty())
        assertTrue(idx.isEmpty())
    }

    @Test
    fun roundJoinGeneratesExtraPieSlices() {
        val (v, idx) = generate(
            listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 0.0), TilePoint(100.0, 100.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Round, beginCap = LineCapType.Butt, endCap = LineCapType.Butt),
        )
        // 90° corner with round join: pie slice vertices added
        assertTrue(v.size > 6, "expected extra pie slices, got ${v.size}")
        assertTrue(idx.size > 12)
    }

    @Test
    fun extrudeMagnitudeIsUnitLength() {
        val (v, _) = generate(
            listOf(TilePoint(0.0, 0.0), TilePoint(100.0, 0.0)),
            PolylineGeneratorOptions(joinType = LineJoinType.Miter, beginCap = LineCapType.Butt, endCap = LineCapType.Butt),
        )
        // extrusions perpendicular to the horizontal line: (0,±1) or (±1,0)
        for (vertex in v) {
            val ex = vertex.extrudeX
            val ey = vertex.extrudeY
            val magSq = ex * ex + ey * ey
            // unit length (allow small epsilon for float math)
            assertTrue(magSq in 0.99..1.01 || magSq == 0.0, "extrude ($ex,$ey) mag=$magSq")
        }
    }
}
