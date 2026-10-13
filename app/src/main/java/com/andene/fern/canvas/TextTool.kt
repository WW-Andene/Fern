package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The TEXT tool's gesture, with its own state rather than in [CanvasState] (roadmap item 167): a
 * tap places (or edits) a label where it lands; a drag past the tap slop draws out a text box
 * instead, whose width the new label's text wraps to - the text-box convention of drawing and
 * whiteboard apps. The entry dialog opens through [host] when the finger lifts.
 */
class TextTool(private val host: Host) {
    interface Host {
        /** Screen pixels per world unit. */
        val scale: Double

        /** Opens the entry dialog for the label at [point], or a new one placed there. */
        fun editTextAt(point: WorldPoint)

        /** Opens the entry dialog for a new label in a box [width] wide with its top-left at [topLeft]. */
        fun newTextBox(topLeft: WorldPoint, width: Double)
    }

    /** While dragging out a text box: its opposite corners, for the preview outline. */
    var boxDraft: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    private var start: WorldPoint? = null

    fun begin(worldPoint: WorldPoint) {
        start = worldPoint
        boxDraft = null
    }

    fun move(worldPoint: WorldPoint) {
        val from = start ?: return
        val movedScreen = hypot(worldPoint.x - from.x, worldPoint.y - from.y) * host.scale
        if (boxDraft != null || movedScreen > CanvasState.TAP_SLOP_SCREEN) boxDraft = from to worldPoint
    }

    fun end() {
        val from = start ?: return
        start = null
        val box = boxDraft
        boxDraft = null
        if (box == null) {
            host.editTextAt(from)
            return
        }
        val (a, b) = box
        host.newTextBox(WorldPoint(minOf(a.x, b.x), minOf(a.y, b.y)), abs(b.x - a.x))
    }

    /** Abandons a gesture that turned into something else (a second finger landed to pinch). */
    fun cancel() {
        start = null
        boxDraft = null
    }
}
