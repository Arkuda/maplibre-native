package org.maplibre.kotlin.style

/** Line cap style. Mirrors mbgl::style::LineCapType. */
enum class LineCapType { Round, Butt, Square }

/** Line join style. Mirrors mbgl::style::LineJoinType. */
enum class LineJoinType {
    Miter,
    Bevel,
    Round,

    /** Internal use only. */
    FakeRound,
    FlipBevel,
}
