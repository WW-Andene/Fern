package com.andene.fern.canvas

/**
 * A layout grid on a frame (roadmap item 142), as in design tools' artboard grids: [columns]
 * columns and [rows] rows (0 for none either way) filling the frame inside a [margin], with a
 * [gutter] between neighbours, all in world units. Shown as tinted bands over the frame; their
 * edges attract moved selections and shape corners.
 */
data class LayoutGrid(val columns: Int, val rows: Int, val gutter: Double, val margin: Double) {
    /** The columns' left and right edges across [frame], in order; empty without columns or room. */
    fun columnEdges(frame: Frame): List<Double> = edges(frame.min.x, frame.max.x, columns)

    /** The rows' top and bottom edges down [frame], in order; empty without rows or room. */
    fun rowEdges(frame: Frame): List<Double> = edges(frame.min.y, frame.max.y, rows)

    private fun edges(from: Double, to: Double, count: Int): List<Double> {
        if (count <= 0) return emptyList()
        val start = from + margin
        val span = (to - margin) - start - gutter * (count - 1)
        if (span <= 0) return emptyList()
        val size = span / count
        return (0 until count).flatMap { i ->
            val left = start + i * (size + gutter)
            listOf(left, left + size)
        }
    }
}

/** Every layout grid edge of [frames] that runs [vertical]ly (x positions), or across (y positions). */
fun layoutGridLines(frames: List<Frame>, vertical: Boolean): List<Double> = frames.flatMap { frame ->
    val grid = frame.layoutGrid ?: return@flatMap emptyList()
    if (vertical) grid.columnEdges(frame) else grid.rowEdges(frame)
}
