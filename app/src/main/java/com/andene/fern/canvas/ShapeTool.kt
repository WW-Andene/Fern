package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The SHAPE tool's gesture, with its own state rather than in [CanvasState] (roadmap item 167):
 * press at one corner, drag to the other, lift to commit. Points snap to objects first (an exact
 * join was asked for), then to the paper's grid or a layout grid, and only then to the shape's
 * own angles and proportions. The shape kind, the snapping and the stroke it becomes are the
 * document's, reached through [host].
 */
class ShapeTool(private val host: Host) {
    interface Host {
        /** Whether a shape can be drawn now (the active layer takes drawing); readies the undo step when it can. */
        fun startShape(): Boolean

        /** A nearby stroke end or corner for [point] when object snapping is on, else null; shows the snap indicator. */
        fun snappedToObject(point: WorldPoint): WorldPoint?
        fun clearSnapIndicator()

        /** [point] on the paper's grid or a frame's layout grid, or [point] itself. */
        fun snappedToGrid(point: WorldPoint): WorldPoint

        /** The end the current shape kind snaps [end] to from [start] (angles, squares, circles). */
        fun snappedShapeEnd(start: WorldPoint, end: WorldPoint): WorldPoint

        /** Adds the shape from [start] to [end] as strokes, completing the undo step [startShape] began. */
        fun commitShape(start: WorldPoint, end: WorldPoint)
    }

    /** The shape being dragged out, (start, snapped end) in world space; null when none is in progress. */
    var preview: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    private var start = WorldPoint.Zero

    fun begin(worldPoint: WorldPoint) {
        if (!host.startShape()) return
        start = host.snappedToObject(worldPoint) ?: host.snappedToGrid(worldPoint)
        preview = start to start
    }

    fun move(worldPoint: WorldPoint) {
        if (preview == null) return
        // An object anchor wins (an exact join was asked for); then the grid, which already gives
        // clean geometry; angle/aspect snapping applies only when neither did.
        host.snappedToObject(worldPoint)?.let {
            preview = start to it
            return
        }
        val end = host.snappedToGrid(worldPoint)
        preview = start to if (end != worldPoint) end else host.snappedShapeEnd(start, worldPoint)
    }

    /** Commits the shape being dragged out. Nothing for a zero-size shape (a press without a drag). */
    fun end() {
        host.clearSnapIndicator()
        val (from, to) = preview ?: return
        preview = null
        if (from == to) return
        host.commitShape(from, to)
    }
}
