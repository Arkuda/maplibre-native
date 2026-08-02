package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.maplibre.kotlin.math.clamp
import org.maplibre.kotlin.math.wrap
import org.maplibre.kotlin.math.ceilLog2
import org.maplibre.kotlin.math.deg2rad
import org.maplibre.kotlin.math.rad2deg
import org.maplibre.kotlin.util.LatLng
import org.maplibre.kotlin.util.LatLngBounds
import org.maplibre.kotlin.util.Projection
import org.maplibre.kotlin.util.ScreenCoordinate
import org.maplibre.kotlin.tile.CanonicalTileID
import org.maplibre.kotlin.tile.OverscaledTileID
import org.maplibre.kotlin.tile.UnwrappedTileID
import kotlin.math.abs

class MathTest {
    @Test
    fun clampWorks() {
        assertEquals(3.0, clamp(5.0, 0.0, 3.0))
        assertEquals(0.0, clamp(-5.0, 0.0, 3.0))
        assertEquals(2.0, clamp(2.0, 0.0, 3.0))
    }

    @Test
    fun wrapWorks() {
        assertEquals(10.0, wrap(370.0, -180.0, 180.0))
        assertEquals(170.0, wrap(-190.0, -180.0, 180.0)) // -190 + 360 = 170
        assertEquals(0.0, wrap(360.0, -180.0, 180.0))
        assertEquals(-180.0, wrap(-180.0, -180.0, 180.0))
        assertEquals(179.0, wrap(179.0, -180.0, 180.0))
    }

    @Test
    fun ceilLog2Works() {
        assertEquals(0, ceilLog2(1))
        assertEquals(1, ceilLog2(2))
        assertEquals(2, ceilLog2(3))
        assertEquals(3, ceilLog2(5))
        assertEquals(10, ceilLog2(1024))
        assertEquals(11, ceilLog2(1025))
    }

    @Test
    fun angleConversions() {
        assertEquals(180.0, rad2deg(deg2rad(180.0)), 1e-9)
        assertEquals(90.0, rad2deg(deg2rad(90.0)), 1e-9)
    }
}

class GeoTest {
    @Test
    fun latLngValidation() {
        assertFailsWith<IllegalArgumentException> { LatLng(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { LatLng(0.0, Double.NaN) }
        LatLng(0.0, 0.0) // ok
        LatLng(-90.0, 180.0) // ok
    }

    @Test
    fun latLngWrapped() {
        val wrapped = LatLng(0.0, 370.0, LatLng.WrapMode.Wrapped)
        assertEquals(10.0, wrapped.longitude, 1e-9)
    }

    @Test
    fun boundsWorld() {
        val world = LatLngBounds.world()
        assertTrue(world.valid)
        assertEquals(-90.0, world.south)
        assertEquals(180.0, world.east)
        assertTrue(world.contains(LatLng(0.0, 0.0)))
    }

    @Test
    fun boundsEmpty() {
        val empty = LatLngBounds.empty()
        assertTrue(empty.isEmpty)
    }

    @Test
    fun boundsHull() {
        val hull = LatLngBounds.hull(LatLng(-10.0, -20.0), LatLng(30.0, 40.0))
        assertEquals(-10.0, hull.south)
        assertEquals(-20.0, hull.west)
        assertEquals(30.0, hull.north)
        assertEquals(40.0, hull.east)
    }

    @Test
    fun boundsConstrain() {
        val world = LatLngBounds.world()
        // input must be a valid LatLng; constrain keeps it inside
        val p = world.constrain(LatLng(80.0, 170.0))
        assertEquals(80.0, p.latitude)
        assertEquals(170.0, p.longitude)
        val p2 = world.constrain(LatLng(-80.0, -170.0))
        assertEquals(-80.0, p2.latitude)
        assertEquals(-170.0, p2.longitude)
    }
}

class ProjectionTest {
    @Test
    fun roundTripProjectUnproject() {
        val latLng = LatLng(45.0, 30.0)
        val scale = 512.0
        val p = Projection.project(latLng, scale)
        val back = Projection.unproject(p, scale)
        assertEquals(latLng.latitude, back.latitude, 1e-9)
        assertEquals(latLng.longitude, back.longitude, 1e-9)
    }

    @Test
    fun projectOriginAtZoom0() {
        // At zoom 0 (scale=2^0=1), world is 512px. (0,0) should project to (256, 256).
        val p = Projection.project(LatLng(0.0, 0.0), 1.0)
        assertEquals(256.0, p.x, 1e-9)
        assertEquals(256.0, p.y, 1e-9)
    }

    @Test
    fun projectedMetersRoundTrip() {
        val latLng = LatLng(52.0, 13.0)
        val meters = Projection.projectedMetersForLatLng(latLng)
        val back = Projection.latLngForProjectedMeters(meters)
        assertEquals(latLng.latitude, back.latitude, 1e-6)
        assertEquals(latLng.longitude, back.longitude, 1e-6)
    }

    @Test
    fun metersPerPixel() {
        // At equator zoom 0, 512px world, circumference 40075016.68m
        val mpp = Projection.getMetersPerPixelAtLatitude(0.0, 0.0)
        assertEquals(40075016.68 / 512.0, mpp, 1.0)
    }
}

class TileIDTest {
    @Test
    fun canonicalChildren() {
        val id = CanonicalTileID(0u, 0u, 0u)
        val children = id.children()
        assertEquals(4, children.size)
        assertTrue(children.all { it.z == 1u.toUByte() })
        assertEquals(children.toSet().size, 4)
    }

    @Test
    fun canonicalScaledTo() {
        val id = CanonicalTileID(5u, 13u, 7u)
        val parent = id.scaledTo(3u)
        assertEquals(3u.toUByte(), parent.z)
        assertEquals(3u, parent.x) // 13 >> 2
        assertEquals(1u, parent.y) // 7 >> 2
        // scaledTo(4): (3,3,1) -> x=3<<1=6, y=1<<1=2
        val child = parent.scaledTo(4u)
        assertEquals(4u.toUByte(), child.z)
        assertEquals(6u, child.x) // 3 << 1
        assertEquals(2u, child.y) // 1 << 1
    }

    @Test
    fun canonicalIsChildOf() {
        val parent = CanonicalTileID(3u, 3u, 1u)
        val child = CanonicalTileID(5u, 13u, 7u)
        assertTrue(child.isChildOf(parent))
        assertTrue(!parent.isChildOf(child))
    }

    @Test
    fun unwrappedFromXY() {
        // x = -1 at z=1 should wrap to wrap=-1, canonical x=1
        val id = UnwrappedTileID(1u, -1L, 0L)
        assertEquals((-1).toShort(), id.wrap)
        assertEquals(1u, id.canonical.x)
    }

    @Test
    fun overscaled() {
        val id = OverscaledTileID(5u, 0, CanonicalTileID(3u, 1u, 1u))
        assertEquals(4u, id.overscaleFactor()) // 1 << (5-3)
        val scaled = id.scaledTo(2u)
        assertEquals(2u.toUByte(), scaled.overscaledZ)
        assertEquals(0u, scaled.canonical.x) // 1 >> 1
    }
}
