package com.andene.fern.canvas

import androidx.compose.runtime.mutableStateListOf
import kotlin.math.hypot

/**
 * The Bézier pen (roadmap item 96), with its own state rather than in [CanvasState] (roadmap item
 * 167): a path built node by node - tap for a corner, drag for a smooth node, tap the first node
 * to close - then committed as a stroke with the current pen settings through [host].
 */
class BezierTool(private val host: Host) {
    interface Host {
        /** Screen pixels per world unit. */
        val scale: Double

        /**
         * Adds a stroke through [points] with the current pen settings, as one undo step; nothing
         * when the active layer can't be drawn on.
         */
        fun commitPenStroke(points: List<WorldPoint>)
    }

    /** Nodes of the path being built; empty when none is in progress. */
    val nodes = mutableStateListOf<BezierNode>()

    /**
     * A press: on the first node of a path of three or more nodes, closes and finishes it;
     * otherwise adds a node here (a corner until dragged).
     */
    fun press(world: WorldPoint) {
        val reach = CLOSE_SCREEN / host.scale.coerceAtLeast(1e-300)
        val first = nodes.firstOrNull()
        if (first != null && nodes.size >= 3 && hypot(first.point.x - world.x, first.point.y - world.y) <= reach) {
            finish(closed = true)
            return
        }
        nodes.add(BezierNode(world))
    }

    /** Dragging while placing a node pulls its handle out, making it smooth. */
    fun dragHandle(world: WorldPoint) {
        val last = nodes.lastOrNull() ?: return
        // Within the tap slop it stays a corner, so a tap with a little wobble isn't curved.
        val moved = hypot(world.x - last.point.x, world.y - last.point.y) * host.scale
        nodes[nodes.lastIndex] = last.copy(handleOut = if (moved <= CanvasState.TAP_SLOP_SCREEN) last.point else world)
    }

    fun removeLastNode() {
        if (nodes.isNotEmpty()) nodes.removeAt(nodes.lastIndex)
    }

    fun cancel() {
        nodes.clear()
    }

    /**
     * Commits the path as a stroke (closed back to its first node when [closed], so the fill tool
     * can fill it), sampled finely enough to look smooth at the current zoom. A path of fewer than
     * two nodes is discarded.
     */
    fun finish(closed: Boolean) {
        val path = nodes.toList()
        nodes.clear()
        if (path.size < 2) return
        host.commitPenStroke(flattenBezier(path, closed && path.size >= 3, SAMPLE_SCREEN / host.scale.coerceAtLeast(1e-300)))
    }

    /** Follows a floating-origin rebase: the world moved by -[offset]. */
    fun shiftBy(offset: WorldPoint) {
        for (i in nodes.indices) nodes[i] = nodes[i].shiftedBy(offset)
    }

    private companion object {
        // Screen pixels: how near the first node a tap closes the path, and how far apart the committed points are.
        const val CLOSE_SCREEN = 24.0
        const val SAMPLE_SCREEN = 4.0
    }
}
