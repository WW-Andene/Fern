package com.andene.fern.canvas

import java.util.BitSet
import kotlin.math.floor

// A view spanning more cells than this on one level - and more cells than that level has
// strokes - reads the level's strokes in full instead of walking cells: zoomed far out, most of
// them are on screen anyway, and walking that many cells would cost more than reading them.
private const val MAX_QUERY_CELLS_PER_LEVEL = 1024

// Each entry packs a cell's 40-bit hash above a 24-bit stroke index: one primitive sort builds
// the whole index. 24 bits hold 16M strokes; past that, those strokes are always candidates.
private const val INDEX_BITS = 24
private const val INDEX_MASK = (1L shl INDEX_BITS) - 1
private const val MAX_INDEXED = 1 shl INDEX_BITS

/**
 * Spatial index over a stroke list, answering "which strokes may intersect this rectangle" in
 * time proportional to the strokes near it rather than to the whole document (roadmap item
 * 49). Built once for a list; [CanvasState.strokesNear] decides when to rebuild it.
 *
 * A multi-level ("loose") grid suits an infinite canvas, where strokes drawn at different zooms
 * differ in size by many orders of magnitude: each stroke goes on the level whose square cells
 * (a power of two in world units) are at least as large as its padded bounds, so it touches at
 * most 2 x 2 cells. A query looks at each level that has strokes, only at the cells the
 * rectangle overlaps.
 *
 * Stored as one sorted LongArray of (cell hash, stroke index) and a per-level stroke list, so
 * building a million-stroke index is a primitive sort rather than millions of map entries.
 * Distinct cells sharing a hash only add candidates. Results are list indices in ascending
 * order, so callers keep the drawing order; they're candidates, to be checked with
 * [Stroke.intersects].
 */
class StrokeSpatialIndex(strokes: List<Stroke>) {
    private val count = strokes.size
    private val entries: LongArray
    private val levelStrokes = HashMap<Int, IntArray>() // level -> every stroke index on it
    // Always returned: strokes past the index's capacity, and any whose cell range came out wider
    // than the 2 x 2 a stroke should touch (only possible from overflow at extreme coordinates).
    private val alwaysCandidates: IntArray

    init {
        val packed = LongArray(minOf(count, MAX_INDEXED) * 4)
        var used = 0
        val levelOf = IntArray(minOf(count, MAX_INDEXED))
        val levelCounts = HashMap<Int, Int>()
        val extra = ArrayList<Int>()
        for (index in 0 until minOf(count, MAX_INDEXED)) {
            val stroke = strokes[index]
            if (stroke.points.isEmpty()) {
                levelOf[index] = EMPTY
                continue
            }
            val pad = stroke.widthWorld
            val level = levelFor(maxOf(stroke.maxX - stroke.minX, stroke.maxY - stroke.minY) + 2 * pad)
            levelOf[index] = level
            levelCounts[level] = (levelCounts[level] ?: 0) + 1
            val size = Math.scalb(1.0, level)
            val x0 = cellOf(stroke.minX - pad, size)
            val x1 = cellOf(stroke.maxX + pad, size)
            val y0 = cellOf(stroke.minY - pad, size)
            val y1 = cellOf(stroke.maxY + pad, size)
            if (x1 - x0 !in 0..1 || y1 - y0 !in 0..1) {
                extra.add(index)
                continue
            }
            for (cx in x0..x1) for (cy in y0..y1) {
                packed[used++] = (cellHash(level, cx, cy) shl INDEX_BITS) or index.toLong()
            }
        }
        for (index in MAX_INDEXED until count) extra.add(index)
        alwaysCandidates = extra.toIntArray()
        entries = packed.copyOf(used).also { it.sort() }
        val fill = HashMap<Int, Int>()
        for ((level, n) in levelCounts) levelStrokes[level] = IntArray(n)
        for (index in levelOf.indices) {
            val level = levelOf[index]
            if (level == EMPTY) continue
            val at = fill[level] ?: 0
            levelStrokes.getValue(level)[at] = index
            fill[level] = at + 1
        }
    }

    /** Indices (ascending) of strokes whose bounds may intersect the rectangle. */
    fun query(left: Double, top: Double, right: Double, bottom: Double): IntArray {
        val found = BitSet(count)
        for ((level, all) in levelStrokes) {
            val size = Math.scalb(1.0, level)
            val x0 = cellOf(left, size)
            val x1 = cellOf(right, size)
            val y0 = cellOf(top, size)
            val y1 = cellOf(bottom, size)
            val width = x1 - x0 + 1
            val height = y1 - y0 + 1
            val limit = maxOf(MAX_QUERY_CELLS_PER_LEVEL, all.size).toLong()
            if (width <= 0 || height <= 0 || width > limit || height > limit || width * height > limit) {
                for (index in all) found.set(index)
                continue
            }
            for (cx in x0..x1) for (cy in y0..y1) {
                val hash = cellHash(level, cx, cy)
                var at = lowerBound(hash shl INDEX_BITS)
                while (at < entries.size && entries[at] ushr INDEX_BITS == hash) {
                    found.set((entries[at] and INDEX_MASK).toInt())
                    at++
                }
            }
        }
        for (index in alwaysCandidates) found.set(index)
        val result = IntArray(found.cardinality())
        var i = 0
        var index = found.nextSetBit(0)
        while (index >= 0) {
            result[i++] = index
            index = found.nextSetBit(index + 1)
        }
        return result
    }

    /** First position in [entries] whose value is at least [value]. */
    private fun lowerBound(value: Long): Int {
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (entries[mid] < value) low = mid + 1 else high = mid
        }
        return low
    }

    private companion object {
        const val EMPTY = Int.MIN_VALUE

        /** Smallest power-of-two exponent whose cell is at least [extent] wide (a dot gets a tiny cell). */
        fun levelFor(extent: Double): Int {
            if (extent.isInfinite()) return 1023
            if (!(extent > 0.0)) return -1074
            val exponent = Math.getExponent(extent)
            return if (Math.scalb(1.0, exponent) >= extent) exponent else exponent + 1
        }

        fun cellOf(coordinate: Double, size: Double): Long = floor(coordinate / size).toLong()

        /** A 40-bit hash of a cell (non-negative, so packed entries sort by it). */
        fun cellHash(level: Int, x: Long, y: Long): Long {
            var h = level.toLong() * -0x61c8864680b583ebL
            h = (h xor x) * -0x40a7b892e31b1a47L
            h = (h xor y) * -0x61c8864680b583ebL
            h = h xor (h ushr 29)
            return h ushr (64 - 40)
        }
    }
}
