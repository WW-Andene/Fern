package com.andene.fern.canvas

import java.util.BitSet
import kotlin.math.floor

// A view spanning more cells than this on one level reads that level's strokes in full instead:
// zoomed far out, most of them are on screen anyway, and walking cells would cost more.
private const val MAX_QUERY_CELLS_PER_LEVEL = 1024

/**
 * Spatial index over a stroke list, answering "which strokes may intersect this rectangle" in
 * time proportional to the strokes near it rather than to the whole document (roadmap item
 * 49). Built once for a list; [CanvasState.strokesNear] decides when to rebuild it.
 *
 * A multi-level ("loose") grid suits an infinite canvas, where strokes drawn at different zooms
 * differ in size by many orders of magnitude: each stroke goes on the level whose square cells
 * (a power of two in world units) are at least as large as its padded bounds, so it touches at
 * most 2 x 2 cells. A query looks at each level that has strokes, only at the cells the
 * rectangle overlaps. Results are list indices in ascending order, so callers keep the drawing
 * order; they're candidates (cells are coarse), to be checked with [Stroke.intersects].
 */
class StrokeSpatialIndex(strokes: List<Stroke>) {
    private data class Cell(val level: Int, val x: Long, val y: Long)

    private val cells = HashMap<Cell, MutableList<Int>>()
    private val levels = HashMap<Int, MutableList<Int>>() // level -> every stroke index on it
    private val count = strokes.size

    init {
        for ((index, stroke) in strokes.withIndex()) {
            if (stroke.points.isEmpty()) continue
            val pad = stroke.widthWorld
            val extent = maxOf(stroke.maxX - stroke.minX, stroke.maxY - stroke.minY) + 2 * pad
            val level = levelFor(extent)
            levels.getOrPut(level) { mutableListOf() }.add(index)
            val size = Math.scalb(1.0, level)
            for (cx in cellOf(stroke.minX - pad, size)..cellOf(stroke.maxX + pad, size)) {
                for (cy in cellOf(stroke.minY - pad, size)..cellOf(stroke.maxY + pad, size)) {
                    cells.getOrPut(Cell(level, cx, cy)) { mutableListOf() }.add(index)
                }
            }
        }
    }

    /** Indices (ascending) of strokes whose bounds may intersect the rectangle. */
    fun query(left: Double, top: Double, right: Double, bottom: Double): IntArray {
        val found = BitSet(count)
        for ((level, all) in levels) {
            val size = Math.scalb(1.0, level)
            val x0 = cellOf(left, size)
            val x1 = cellOf(right, size)
            val y0 = cellOf(top, size)
            val y1 = cellOf(bottom, size)
            val width = x1 - x0 + 1
            val height = y1 - y0 + 1
            if (width <= 0 || height <= 0 || width > MAX_QUERY_CELLS_PER_LEVEL || width * height > MAX_QUERY_CELLS_PER_LEVEL) {
                for (index in all) found.set(index)
                continue
            }
            for (cx in x0..x1) for (cy in y0..y1) cells[Cell(level, cx, cy)]?.forEach { found.set(it) }
        }
        val result = IntArray(found.cardinality())
        var i = 0
        var index = found.nextSetBit(0)
        while (index >= 0) {
            result[i++] = index
            index = found.nextSetBit(index + 1)
        }
        return result
    }

    private companion object {
        /** Smallest power-of-two exponent whose cell is at least [extent] wide (a dot gets a tiny cell). */
        fun levelFor(extent: Double): Int {
            if (!(extent > 0.0) || extent.isInfinite()) return if (extent.isInfinite()) Int.MAX_VALUE / 2 else -1074
            val exponent = Math.getExponent(extent)
            return if (Math.scalb(1.0, exponent) >= extent) exponent else exponent + 1
        }

        fun cellOf(coordinate: Double, size: Double): Long = floor(coordinate / size).toLong()
    }
}
