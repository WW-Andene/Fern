package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The ERASER tool's gesture, with its own state rather than in [CanvasState] (roadmap item 167):
 * rubbing out where it passes, or with [lassoErase] drawing a loop whose inside goes when the
 * finger lifts; on a raster layer it paints transparency. What it erases, the raster painting it
 * shares with the pen, the lasso path it shares with the select tool and undo are the document's,
 * reached through [host].
 */
class EraserTool(private val host: Host) {
    interface Host {
        /** Readies the undo step and drops the selection (an erase may split or remove selected strokes); returns the undo depth before it. */
        fun startErase(): Int

        /** Begins erasing pixels at [point] when the active layer is an editable raster layer; returns whether it did. */
        fun beginRasterErase(point: WorldPoint): Boolean

        /** Whether a raster stroke (painting or erasing pixels) is in progress. */
        val rasterStrokeActive: Boolean
        fun continueRasterErase(point: WorldPoint)
        fun endRasterErase()

        /** Rubs out what's under [point]: ink cut, or whole objects (see [CanvasState.eraseWholeObjects]). */
        fun eraseAt(point: WorldPoint)

        /** The loop being drawn, shown on the canvas; shared with the select tool's lasso. */
        val lassoPath: MutableList<WorldPoint>

        /** Erases inside the closed loop [lasso]; returns whether anything was erased. */
        fun eraseInsideLasso(lasso: List<WorldPoint>): Boolean

        /** Drops the history recorded since undo depth [depth]. */
        fun discardActionsSince(depth: Int)

        /** Counts an erase begun at [startMillis] in the drawing statistics. */
        fun countErase(startMillis: Long)

        /** Strokes or images were erased: save and redraw them. */
        fun erased()
    }

    /**
     * Lasso eraser: instead of rubbing out where the eraser passes, the eraser gesture draws a
     * loop and everything inside it is erased when the finger lifts - ink cut at the loop's edge
     * in ink mode, whole strokes and images in object mode (see [CanvasState.eraseWholeObjects]).
     */
    var lassoErase by mutableStateOf(false)

    /** An erase is in progress (the canvas draws live rather than from its tile cache). */
    var isErasing = false
        private set

    private var startMillis = 0L
    private var startDepth = 0

    fun begin(worldPoint: WorldPoint) {
        isErasing = true
        startMillis = System.currentTimeMillis()
        startDepth = host.startErase()
        if (host.beginRasterErase(worldPoint)) return
        if (lassoErase) host.lassoPath.add(worldPoint) else host.eraseAt(worldPoint)
    }

    fun move(worldPoint: WorldPoint) {
        if (host.rasterStrokeActive) {
            host.continueRasterErase(worldPoint)
            return
        }
        if (lassoErase) {
            if (host.lassoPath.isNotEmpty()) host.lassoPath.add(worldPoint)
        } else {
            host.eraseAt(worldPoint)
        }
    }

    fun end() {
        isErasing = false
        if (host.rasterStrokeActive) {
            host.endRasterErase()
            host.countErase(startMillis)
            return
        }
        if (lassoErase) {
            val lasso = host.lassoPath.toList()
            host.lassoPath.clear()
            // A tap or a line encloses nothing: drop the history entry instead of leaving an empty undo step.
            if (lasso.size < 3 || !host.eraseInsideLasso(lasso)) {
                host.discardActionsSince(startDepth)
                return
            }
        }
        host.countErase(startMillis)
        host.erased()
    }
}
