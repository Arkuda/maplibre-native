package org.maplibre.kotlin.tile

import org.maplibre.kotlin.util.EXTENT
import org.maplibre.kotlin.util.TILE_SIZE
import kotlin.math.min
import kotlin.math.pow

/**
 * Has integer z/x/y coordinates. All tiles must be derived from 0/0/0 (=no
 * tiles outside of the main tile pyramid). Used for requesting data;
 * represents data tiles that exist out there. z is never larger than the
 * source's maxzoom.
 */
data class CanonicalTileID(val z: UByte, val x: UInt, val y: UInt) : Comparable<CanonicalTileID> {
    init {
        require(z <= 32u) { "z must be <= 32" }
        require(x < (1u shl z.toInt())) { "x must be < 2^z" }
        require(y < (1u shl z.toInt())) { "y must be < 2^z" }
    }

    fun isChildOf(parent: CanonicalTileID): Boolean {
        // We're first testing for z == 0, to avoid a 32 bit shift, which is undefined.
        return parent.z == 0.toUByte() ||
            (parent.z < z && parent.x == (x shr (z - parent.z).toInt()) && parent.y == (y shr (z - parent.z).toInt()))
    }

    fun scaledTo(targetZ: UByte): CanonicalTileID {
        return if (targetZ <= z) {
            // parent or same
            CanonicalTileID(targetZ, x shr (z - targetZ).toInt(), y shr (z - targetZ).toInt())
        } else {
            // child
            CanonicalTileID(targetZ, x shl (targetZ - z).toInt(), y shl (targetZ - z).toInt())
        }
    }

    fun children(): List<CanonicalTileID> {
        val childZ = (z + 1u).toUByte()
        val childX = x * 2u
        val childY = y * 2u
        return listOf(
            CanonicalTileID(childZ, childX, childY),
            CanonicalTileID(childZ, childX, childY + 1u),
            CanonicalTileID(childZ, childX + 1u, childY),
            CanonicalTileID(childZ, childX + 1u, childY + 1u),
        )
    }

    override fun compareTo(other: CanonicalTileID): Int {
        return compareValuesBy(this, other, { it.z }, { it.x }, { it.y })
    }
}

/**
 * Has integer z/x/y coordinates. overscaledZ describes the zoom level this
 * tile is intended to represent, e.g. when parsing data z is never larger
 * than the source's maxzoom.
 */
data class OverscaledTileID(
    val overscaledZ: UByte,
    val wrap: Short,
    val canonical: CanonicalTileID,
) : Comparable<OverscaledTileID> {
    init {
        require(overscaledZ >= canonical.z) { "overscaledZ must be >= canonical.z" }
    }

    constructor(z: UByte, x: UInt, y: UInt) : this(z, 0, CanonicalTileID(z, x, y))

    constructor(canonical: CanonicalTileID) : this(canonical.z, 0, canonical)

    fun isChildOf(rhs: OverscaledTileID): Boolean =
        wrap == rhs.wrap && overscaledZ > rhs.overscaledZ &&
            (canonical == rhs.canonical || canonical.isChildOf(rhs.canonical))

    fun overscaleFactor(): UInt = 1u shl (overscaledZ - canonical.z).toInt()

    fun scaledTo(z: UByte): OverscaledTileID {
        val newCanonical = if (z >= canonical.z) canonical else canonical.scaledTo(z)
        return OverscaledTileID(z, wrap, newCanonical)
    }

    fun toUnwrapped(): UnwrappedTileID = UnwrappedTileID(wrap, canonical)

    fun unwrapTo(newWrap: Short): OverscaledTileID = OverscaledTileID(overscaledZ, newWrap, canonical)

    override fun compareTo(other: OverscaledTileID): Int {
        return compareValuesBy(this, other, { it.overscaledZ }, { it.wrap }, { it.canonical })
    }
}

/**
 * Has integer z/x/y coordinates. wrap describes tiles that are left/right of
 * the main tile pyramid, e.g. when wrapping the world. Used for describing
 * what position tiles are getting rendered at (= calc the matrix). z is never
 * larger than the source's maxzoom.
 */
data class UnwrappedTileID(
    val wrap: Short,
    val canonical: CanonicalTileID,
) : Comparable<UnwrappedTileID> {
    constructor(z: UByte, x: Long, y: Long) : this(
        ((if (x < 0) x - (1L shl z.toInt()) + 1 else x) / (1L shl z.toInt())).toShort(),
        CanonicalTileID(
            z,
            (x - ((if (x < 0) x - (1L shl z.toInt()) + 1 else x) / (1L shl z.toInt())) * (1L shl z.toInt())).toUInt(),
            if (y < 0) 0u else min(y.toUInt(), (1uL shl z.toInt()).toUInt() - 1u),
        ),
    )

    fun isChildOf(parent: UnwrappedTileID): Boolean =
        wrap == parent.wrap && canonical.isChildOf(parent.canonical)

    fun children(): List<UnwrappedTileID> {
        val childZ = (canonical.z + 1u).toUByte()
        val childX = canonical.x * 2u
        val childY = canonical.y * 2u
        return listOf(
            UnwrappedTileID(wrap, CanonicalTileID(childZ, childX, childY)),
            UnwrappedTileID(wrap, CanonicalTileID(childZ, childX, childY + 1u)),
            UnwrappedTileID(wrap, CanonicalTileID(childZ, childX + 1u, childY)),
            UnwrappedTileID(wrap, CanonicalTileID(childZ, childX + 1u, childY + 1u)),
        )
    }

    fun overscaleTo(overscaledZ: UByte): OverscaledTileID {
        require(overscaledZ >= canonical.z)
        return OverscaledTileID(overscaledZ, wrap, canonical)
    }

    fun pixelsToTileUnits(pixelValue: Float, zoom: Float): Float =
        pixelValue * (EXTENT.toFloat() / (TILE_SIZE.toFloat() * 2f.pow(zoom - canonical.z.toFloat())))

    fun unwrapTo(newWrap: Short): UnwrappedTileID = UnwrappedTileID(newWrap, canonical)

    override fun compareTo(other: UnwrappedTileID): Int {
        return compareValuesBy(this, other, { it.wrap }, { it.canonical })
    }
}
