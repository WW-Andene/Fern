package com.andene.fern.canvas

import kotlin.math.ceil
import kotlin.math.floor

/** The name of the frame wrap-around mode tiles (roadmap item 137); made from the view if there isn't one. */
const val WRAP_TILE_FRAME_NAME = "Tile"

/** At most this many copies of the tile are drawn across the view each way, so zooming far out stays fast. */
const val WRAP_MAX_REPEATS = 7

/**
 * The offsets (world units, whole tiles of [tile]) at which wrap-around mode (roadmap item 137,
 * Krita's and Aseprite's tiled mode) draws the drawing again so it repeats across the view
 * [viewMin]..[viewMax]: every copy the view touches, the tile itself (0, 0) among them, at most
 * [WRAP_MAX_REPEATS] each way around the tile.
 */
fun wrapOffsets(tile: Frame, viewMin: WorldPoint, viewMax: WorldPoint): List<WorldPoint> {
    val w = tile.max.x - tile.min.x
    val h = tile.max.y - tile.min.y
    if (w <= 0.0 || h <= 0.0) return listOf(WorldPoint.Zero)
    val half = WRAP_MAX_REPEATS / 2
    // Content sits within a tile of the tile (see [wrappedIntoTile]), so a copy one tile beyond the view can still reach into it.
    val i0 = (floor((viewMin.x - tile.max.x) / w).toInt()).coerceIn(-half, half)
    val i1 = (ceil((viewMax.x - tile.min.x) / w).toInt()).coerceIn(-half, half)
    val j0 = (floor((viewMin.y - tile.max.y) / h).toInt()).coerceIn(-half, half)
    val j1 = (ceil((viewMax.y - tile.min.y) / h).toInt()).coerceIn(-half, half)
    return (j0..j1).flatMap { j -> (i0..i1).map { i -> WorldPoint(i * w, j * h) } }
}

/**
 * How far to shift a stroke starting at [start] so it starts inside [tile]: whole tiles, so in
 * wrap-around mode ink drawn on any repeat lands on the tile itself and all of it is drawn (and
 * exported) wherever the tile repeats. [WorldPoint.Zero] when it already starts inside.
 */
fun wrappedIntoTile(tile: Frame, start: WorldPoint): WorldPoint {
    val w = tile.max.x - tile.min.x
    val h = tile.max.y - tile.min.y
    if (w <= 0.0 || h <= 0.0) return WorldPoint.Zero
    return WorldPoint(floor((start.x - tile.min.x) / w) * w, floor((start.y - tile.min.y) / h) * h)
}
