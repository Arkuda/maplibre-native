package org.maplibre.kotlin.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Earcut polygon triangulation. Port of mapbox/earcut.hpp (which is a port
 * of earcut.js) used by MapLibre for fill layers.
 *
 * Input: a flat list of vertex coordinates [x0, y0, x1, y1, ...] with
 * optional hole start indices. Output: a flat list of triangle vertex
 * indices into the input array (each triangle = 3 indices).
 */
object Earcut {

    /** Triangulates a flat polygon array. */
    fun triangulate(data: DoubleArray, holeIndices: IntArray = IntArray(0), dim: Int = 2): IntArray {
        val hasHoles = holeIndices.isNotEmpty()
        val outerLen = if (hasHoles) holeIndices[0] * dim else data.size
        var outerNode = linkedList(data, 0, outerLen, dim, true)
        val triangles = mutableListOf<Int>()
        if (outerNode == null || outerNode.next === outerNode.prev) return IntArray(0)

        var minX = 0.0
        var minY = 0.0
        var maxX = 0.0
        var maxY = 0.0
        var invSize = 0.0

        if (hasHoles) outerNode = eliminateHoles(data, holeIndices, outerNode, dim)

        if (data.size > 80 * dim) {
            minX = data[0]
            maxX = data[0]
            minY = data[1]
            maxY = data[1]
            var i = dim
            while (i < outerLen) {
                val x = data[i]
                val y = data[i + 1]
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                i += dim
            }
            invSize = max(maxX - minX, maxY - minY)
            invSize = if (invSize != 0.0) 32767.0 / invSize else 0.0
        }

        earcutLinked(outerNode, triangles, dim, minX, minY, invSize, 0)
        return triangles.toIntArray()
    }

    // --- linked list of polygon nodes ---

    private class Node(
        var i: Int,
        var x: Double,
        var y: Double,
    ) {
        var prev: Node = this
        var next: Node = this
        var z: Int = 0
        var prevZ: Node? = null
        var nextZ: Node? = null
        var steiner: Boolean = false
    }

    private fun linkedList(data: DoubleArray, start: Int, end: Int, dim: Int, clockwise: Boolean): Node? {
        var last: Node? = null
        var i = start
        if (clockwise == (signedArea(data, start, end, dim) > 0)) {
            while (i < end) {
                last = insertNode(i, data[i], data[i + 1], last)
                i += dim
            }
        } else {
            i = end - dim
            while (i >= start) {
                last = insertNode(i, data[i], data[i + 1], last)
                i -= dim
            }
        }
        if (last != null && equals(last, last.next)) {
            removeNode(last)
            last = last.next
        }
        return last
    }

    // eliminate colinear or duplicate points
    private fun filterPoints(start: Node?, end: Node? = null): Node? {
        var s = start
        var e = end ?: start
        if (s == null || e == null) return s
        if (s === e) s = s.next!!
        var last = s
        var p: Node? = s
        do {
            if (!equals(p!!, p.next) && area(p.prev, p, p.next) != 0.0) {
                last = p
                p = p.next
            } else {
                val next = p.next
                removeNode(p)
                p = next
                if (p === last) break
            }
        } while (p !== s)
        return last
    }

    // main ear slicing loop which triangulates a polygon (given as a linked list)
    private fun earcutLinked(
        start: Node?,
        triangles: MutableList<Int>,
        dim: Int,
        minX: Double,
        minY: Double,
        invSize: Double,
        pass: Int,
    ) {
        var ear = start ?: return

        // interlink polygon nodes in z-order
        if (pass == 0 && invSize != 0.0) indexCurve(ear, minX, minY, invSize)

        var stop = ear
        var prev: Node?
        var next: Node?

        // iterate through ears, slicing them one by one
        while (ear.prev !== ear.next) {
            prev = ear.prev
            next = ear.next

            if (if (invSize != 0.0) isEarHashed(ear, minX, minY, invSize) else isEar(ear)) {
                // cut off the triangle
                triangles.add(prev.i / dim)
                triangles.add(ear.i / dim)
                triangles.add(next.i / dim)

                removeNode(ear)

                // skipping the next vertex leads to less sliver triangles
                ear = next.next!!
                stop = ear
                continue
            }

            ear = next!!

            // if we looped through the whole remaining polygon and can't find any more ears
            if (ear === stop) {
                // try filtering points and slicing again
                if (pass == 0) {
                    earcutLinked(filterPoints(ear), triangles, dim, minX, minY, invSize, 1)

                    // if this didn't work, try curing all small self-intersections locally
                } else if (pass == 1) {
                    ear = cureLocalIntersections(filterPoints(ear)!!, triangles, dim) ?: break
                    earcutLinked(ear, triangles, dim, minX, minY, invSize, 2)

                    // as a last resort, try splitting the remaining polygon into two
                } else if (pass == 2) {
                    splitEarcut(ear, triangles, dim, minX, minY, invSize)
                }
                break
            }
        }
    }

    // check whether a polygon node forms a valid ear
    private fun isEar(ear: Node): Boolean {
        val a = ear.prev
        val b = ear
        val c = ear.next

        // reflex, can't be an ear
        if (area(a, b, c) >= 0) return false

        // now make sure we don't have other points inside the potential ear
        var p: Node? = ear.next.next
        while (p !== ear.prev) {
            if (p != null && pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, p.x, p.y) &&
                area(p.prev, p, p.next) >= 0
            ) return false
            p = p!!.next
        }
        return true
    }

    private fun isEarHashed(ear: Node, minX: Double, minY: Double, invSize: Double): Boolean {
        val a = ear.prev
        val b = ear
        val c = ear.next

        if (area(a, b, c) >= 0) return false // reflex, can't be an ear

        // triangle bbox; min & max are calculated like this for speed
        val minTX = if (a.x < b.x) if (a.x < c.x) a.x else c.x else if (b.x < c.x) b.x else c.x
        val minTY = if (a.y < b.y) if (a.y < c.y) a.y else c.y else if (b.y < c.y) b.y else c.y
        val maxTX = if (a.x > b.x) if (a.x > c.x) a.x else c.x else if (b.x > c.x) b.x else c.x
        val maxTY = if (a.y > b.y) if (a.y > c.y) a.y else c.y else if (b.y > c.y) b.y else c.y

        // z-order range for the current triangle bbox
        val minZ = zOrder(minTX, minTY, minX, minY, invSize)
        val maxZ = zOrder(maxTX, maxTY, minX, minY, invSize)

        var p = ear.prevZ
        var n = ear.nextZ

        // look for points inside the triangle in both directions
        while (p != null && p.z >= minZ && n != null && n.z <= maxZ) {
            if (p !== ear.prev && p !== ear.next &&
                pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, p.x, p.y) &&
                area(p.prev, p, p.next) >= 0
            ) return false
            p = p.prevZ

            if (n !== ear.prev && n !== ear.next &&
                pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, n.x, n.y) &&
                area(n.prev!!, n, n.next!!) >= 0
            ) return false
            n = n.nextZ
        }

        // look for remaining points in decreasing z-order
        while (p != null && p.z >= minZ) {
            if (p !== ear.prev && p !== ear.next &&
                pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, p.x, p.y) &&
                area(p.prev, p, p.next) >= 0
            ) return false
            p = p.prevZ
        }

        // look for remaining points in increasing z-order
        while (n != null && n.z <= maxZ) {
            if (n !== ear.prev && n !== ear.next &&
                pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, n.x, n.y) &&
                area(n.prev!!, n, n.next!!) >= 0
            ) return false
            n = n.nextZ
        }
        return true
    }

    // go through all polygon nodes and cure small local self-intersections
    private fun cureLocalIntersections(start: Node, triangles: MutableList<Int>, dim: Int): Node? {
        var p = start
        var loopStart = start
        do {
            val a = p.prev
            val b = p.next.next

            if (!equals(a, b) && intersects(a, p, p.next, b) && locallyInside(a, b) && locallyInside(b, a)) {
                triangles.add(a.i / dim)
                triangles.add(p.i / dim)
                triangles.add(b.i / dim)

                // remove two nodes involved
                removeNode(p)
                removeNode(p.next)
                p = b
                loopStart = b
            }
            p = p.next
        } while (p !== loopStart)
        return filterPoints(p)
    }

    // try splitting polygon into two and triangulate them independently
    private fun splitEarcut(
        start: Node,
        triangles: MutableList<Int>,
        dim: Int,
        minX: Double,
        minY: Double,
        invSize: Double,
    ) {
        // look for a valid diagonal that divides the polygon into two
        var a = start
        do {
            var b = a.next.next
            while (b !== a.prev) {
                if (a.i != b.i && isValidDiagonal(a, b)) {
                    // split the polygon in two by the diagonal
                    val c = splitPolygon(a, b)

                    // filter colinear points around the cuts
                    a = filterPoints(a, a.next)!!
                    val c2 = filterPoints(c, c.next)!!

                    // run earcut on each half
                    earcutLinked(a, triangles, dim, minX, minY, invSize, 0)
                    earcutLinked(c2, triangles, dim, minX, minY, invSize, 0)
                    return
                }
                b = b.next
            }
            a = a.next
        } while (a !== start)
    }

    // link every hole into the outer loop, producing a single-ring polygon without holes
    private fun eliminateHoles(data: DoubleArray, holeIndices: IntArray, outerNode: Node, dim: Int): Node? {
        val queue = mutableListOf<Node>()

        var len = holeIndices.size
        var i = 0
        var start = 0
        var end = 0
        while (i < len) {
            start = holeIndices[i] * dim
            end = if (i < len - 1) holeIndices[i + 1] * dim else data.size
            val list = linkedList(data, start, end, dim, false)
            if (list != null) {
                if (list === list.next) {
                    list.steiner = true
                }
                queue.add(getLeftmost(list))
            }
            i++
        }

        queue.sortWith(compareBy({ it.x }, { it.y }))

        // process holes from left to right
        var outer: Node? = outerNode
        for (node in queue) {
            outer = eliminateHole(node, outer!!)
        }
        return outer
    }

    private fun eliminateHole(hole: Node, outerNode: Node): Node? {
        val bridge = findHoleBridge(hole, outerNode) ?: return outerNode

        val bridgeReverse = splitPolygon(bridge, hole)

        // filter collinear points around the cuts
        filterPoints(bridgeReverse, bridgeReverse.next)
        return filterPoints(bridge, bridge.next)
    }

    // David Eberly's algorithm for finding a bridge between hole and outer polygon
    private fun findHoleBridge(hole: Node, outerNode: Node): Node? {
        var p = outerNode
        val hx = hole.x
        val hy = hole.y
        var qx = Double.NEGATIVE_INFINITY
        var m: Node? = null

        // find a segment intersected by a ray from the hole's leftmost point to the left;
        // segment's endpoint with lesser x will be potential connection point
        do {
            if (hy <= p.y && hy >= p.next.y && p.next.y != p.y) {
                val x = p.x + (hy - p.y) * (p.next.x - p.x) / (p.next.y - p.y)
                if (x <= hx && x > qx) {
                    qx = x
                    m = if (p.x < p.next.x) p else p.next
                    if (x == hx) return m // hole touches outer segment; pick leftmost endpoint
                }
            }
            p = p.next
        } while (p !== outerNode)

        if (m == null) return null

        // look for points inside the triangle of hole point, segment intersection and endpoint;
        // if there are no points found, we have a valid connection;
        // otherwise choose the point of the minimum angle with the ray as connection point
        var mm: Node = m
        val stop = mm
        val mx = mm.x
        val my = mm.y
        var tanMin = Double.POSITIVE_INFINITY

        var pp = mm
        do {
            if (hx >= pp.x && pp.x >= mx && hx != pp.x &&
                pointInTriangle(if (hy < my) hx else qx, hy, mx, my, if (hy < my) qx else hx, hy, pp.x, pp.y)
            ) {
                val tan = abs(hy - pp.y) / (hx - pp.x) // tangential
                if (locallyInside(pp, hole) &&
                    (tan < tanMin || (tan == tanMin && (pp.x > mm.x || (pp.x == mm.x && sectorContainsSector(mm, pp)))))
                ) {
                    mm = pp
                    tanMin = tan
                }
            }
            pp = pp.next
        } while (pp !== stop)
        return mm
    }

    // whether sector in vertex m contains sector in vertex p in the same coordinates
    private fun sectorContainsSector(m: Node, p: Node): Boolean =
        area(m.prev, m, p.prev) < 0 && area(p.next, m, m.next) < 0

    // interlink polygon nodes in z-order
    private fun indexCurve(start: Node, minX: Double, minY: Double, invSize: Double) {
        var p = start
        do {
            if (p.z == 0) p.z = zOrder(p.x, p.y, minX, minY, invSize)
            p.prevZ = p.prev
            p.nextZ = p.next
            p = p.next
        } while (p !== start)

        p.prevZ!!.nextZ = null
        p.prevZ = null

        sortLinked(p)
    }

    // Simon Tatham's linked list merge sort algorithm. Returns the new head.
    private fun sortLinked(list: Node?): Node? {
        var head = list
        var numMerges: Int
        var inSize = 1
        var p: Node?
        var psize: Int
        var q: Node?
        var qsize: Int
        var tail: Node?

        do {
            p = head
            tail = null
            numMerges = 0
            while (p != null) {
                numMerges++
                q = p
                psize = 0
                while (psize < inSize && q != null) {
                    psize++
                    q = q.nextZ
                }
                qsize = inSize
                while (psize > 0 || (qsize > 0 && q != null)) {
                    if (psize != 0 && (qsize == 0 || q == null || p!!.z <= q.z)) {
                        val e = p
                        p = p!!.nextZ
                        psize--
                        if (tail != null) tail.nextZ = e
                        else head = e
                        e.prevZ = tail
                        tail = e
                    } else {
                        val e = q
                        q = q!!.nextZ
                        qsize--
                        if (tail != null) tail.nextZ = e
                        else head = e
                        e.prevZ = tail
                        tail = e
                    }
                }
                p = q
            }
            tail!!.nextZ = null
            inSize *= 2
        } while (numMerges > 1)
        return head
    }

    // z-order of a point given coords and inverse of the longer side of data bbox
    private fun zOrder(x: Double, y: Double, minX: Double, minY: Double, invSize: Double): Int {
        // coords are transformed into non-negative 15-bit integer range
        val ix = (32767 * (x - minX) * invSize).toInt()
        val iy = (32767 * (y - minY) * invSize).toInt()

        val xb = ix
        val yb = iy
        // extract the 5th, 10th and 15th bits
        var b0 = xb and 0x4000
        val b1 = xb and 0x2000
        val b2 = xb and 0x1000
        val b3 = xb and 0x0800
        val b4 = xb and 0x0400
        val b5 = xb and 0x0200
        val b6 = xb and 0x0100
        val b7 = xb and 0x0080
        val b8 = xb and 0x0040
        val b9 = xb and 0x0020
        val b10 = xb and 0x0010
        val b11 = xb and 0x0008
        val b12 = xb and 0x0004
        val b13 = xb and 0x0002
        val b14 = xb and 0x0001
        var c0 = yb and 0x4000
        var c1 = yb and 0x2000
        var c2 = yb and 0x1000
        var c3 = yb and 0x0800
        var c4 = yb and 0x0400
        var c5 = yb and 0x0200
        var c6 = yb and 0x0100
        var c7 = yb and 0x0080
        var c8 = yb and 0x0040
        var c9 = yb and 0x0020
        var c10 = yb and 0x0010
        var c11 = yb and 0x0008
        var c12 = yb and 0x0004
        var c13 = yb and 0x0002
        var c14 = yb and 0x0001

        return b0 or (b1 shl 1) or (b2 shl 2) or (b3 shl 3) or (b4 shl 4) or (b5 shl 5) or (b6 shl 6) or (b7 shl 7) or
            (b8 shl 8) or (b9 shl 9) or (b10 shl 10) or (b11 shl 11) or (b12 shl 12) or (b13 shl 13) or (b14 shl 14) or
            (c0 shl 1) or (c1 shl 2) or (c2 shl 3) or (c3 shl 4) or (c4 shl 5) or (c5 shl 6) or (c6 shl 7) or
            (c7 shl 8) or (c8 shl 9) or (c9 shl 10) or (c10 shl 11) or (c11 shl 12) or (c12 shl 13) or (c13 shl 14) or
            (c14 shl 15)
    }

    // find the leftmost node of a polygon ring
    private fun getLeftmost(start: Node): Node {
        var p = start
        var leftmost = start
        do {
            if (p.x < leftmost.x || (p.x == leftmost.x && p.y < leftmost.y)) leftmost = p
            p = p.next
        } while (p !== start)
        return leftmost
    }

    // check if a diagonal between two polygon nodes is valid (lies in polygon interior)
    private fun isValidDiagonal(a: Node, b: Node): Boolean =
        a.next.i != b.i && a.prev.i != b.i &&
            !intersectsPolygon(a, b) && // dones't intersect other edges
            (locallyInside(a, b) && locallyInside(b, a) && middleInside(a, b) &&
                (area(a.prev, a, b.prev) != 0.0 || area(a, b.prev, b) != 0.0) // locally visible
                || equals(a, b) && area(a.prev, a, a.next) > 0 && area(b.prev, b, b.next) > 0) // zero-length diagonal

    // check if the middle point of a polygon diagonal is inside the polygon
    private fun middleInside(a: Node, b: Node): Boolean {
        var p = a
        var inside = false
        val px = (a.x + b.x) / 2
        val py = (a.y + b.y) / 2
        do {
            if ((p.y > py) != (p.next.y > py) && p.next.y != p.y &&
                (px < (p.next.x - p.x) * (py - p.y) / (p.next.y - p.y) + p.x)
            ) inside = !inside
            p = p.next
        } while (p !== a)
        return inside
    }

    // check if a polygon diagonal intersects any polygon segments
    private fun intersectsPolygon(a: Node, b: Node): Boolean {
        var p = a
        do {
            if (p.i != a.i && p.next.i != a.i && p.i != b.i && p.next.i != b.i &&
                intersects(p, p.next, a, b)
            ) return true
            p = p.next
        } while (p !== a)
        return false
    }

    // check if a polygon diagonal is locally inside the polygon
    private fun locallyInside(a: Node, b: Node): Boolean =
        if (area(a.prev, a, a.next) < 0) {
            area(a, b, a.next) >= 0 && area(a, a.prev, b) >= 0
        } else {
            area(a, b, a.prev) < 0 || area(a, a.next, b) < 0
        }

    // check if the middle point of a polygon diagonal is inside the polygon
    private fun splitPolygon(a: Node, b: Node): Node {
        val a2 = Node(a.i, a.x, a.y)
        val b2 = Node(b.i, b.x, b.y)
        val an = a.next
        val bp = b.prev

        a.next = b
        b.prev = a

        a2.next = an
        an!!.prev = a2

        b2.next = a2
        a2.prev = b2

        bp!!.next = b2
        b2.prev = bp

        return b2
    }

    // create a node and optionally link it with previous one (in a circular doubly linked list)
    private fun insertNode(i: Int, x: Double, y: Double, last: Node?): Node {
        val p = Node(i, x, y)
        if (last == null) {
            p.prev = p
            p.next = p
        } else {
            p.next = last.next
            p.prev = last
            last.next.prev = p
            last.next = p
        }
        return p
    }

    private fun removeNode(p: Node) {
        p.next.prev = p.prev
        p.prev.next = p.next
    }

    // signed area of a triangle
    private fun area(p: Node, q: Node, r: Node): Double =
        (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

    // signed area of a polygon
    private fun signedArea(data: DoubleArray, start: Int, end: Int, dim: Int): Double {
        var sum = 0.0
        var j = end - dim
        var i = start
        while (i < end) {
            sum += (data[j] - data[i]) * (data[i + 1] + data[j + 1])
            j = i
            i += dim
        }
        return sum
    }

    // check if a point lies within a convex triangle
    private fun pointInTriangle(
        ax: Double, ay: Double,
        bx: Double, by: Double,
        cx: Double, cy: Double,
        px: Double, py: Double,
    ): Boolean =
        (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
            (ax - px) * (by - py) >= (bx - px) * (ay - py) &&
            (bx - px) * (cy - py) >= (cx - px) * (by - py)

    // check if a diagonal between two points is strictly inside the polygon
    private fun intersects(p1: Node, p2: Node, q1: Node, q2: Node): Boolean {
        val o1 = sign(area(p1, p2, q1))
        val o2 = sign(area(p1, p2, q2))
        val o3 = sign(area(q1, q2, p1))
        val o4 = sign(area(q1, q2, p2))

        if (o1 != o2 && o3 != o4) return true // general case

        if (o1 == 0 && onSegment(p1, p2, q1)) return true // p1, p2 and q1 are collinear and q1 lies on p1p2
        if (o2 == 0 && onSegment(p1, p2, q2)) return true // p1, p2 and q2 are collinear and q2 lies on p1p2
        if (o3 == 0 && onSegment(q1, q2, p1)) return true // q1, q2 and p1 are collinear and p1 lies on q1q2
        if (o4 == 0 && onSegment(q1, q2, p2)) return true // q1, q2 and p2 are collinear and p2 lies on q1q2
        return false
    }

    private fun onSegment(p: Node, q: Node, r: Node): Boolean =
        q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) && q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)

    private fun sign(num: Double): Int = if (num > 0) 1 else if (num < 0) -1 else 0

    private fun equals(p1: Node?, p2: Node?): Boolean = p1!!.x == p2!!.x && p1.y == p2.y
}
