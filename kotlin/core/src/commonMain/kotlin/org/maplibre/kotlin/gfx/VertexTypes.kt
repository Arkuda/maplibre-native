package org.maplibre.kotlin.gfx

/**
 * Ported from mbgl::gfx::Vertex types.
 */
data class FillLayoutVertex(val a1: Array<Short>)

data class LineLayoutVertex(
    val a1: Array<Short>,
    val a2: Array<Byte>
)
