package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot

/**
 * The MEASURE tool (roadmap item 143), with its own state rather than in [CanvasState] (roadmap
 * item 167): drag to measure a line, or tap to measure what's under the finger. [host] gives it
 * the document and the view.
 */
class MeasureTool(private val host: Host) {
    interface Host {
        /** Screen pixels per world unit. */
        val scale: Double
        val strokes: List<Stroke>

        /** A nearby stroke end or corner for [point] when object snapping is on, else null; shows the snap indicator. */
        fun snappedToObject(point: WorldPoint): WorldPoint?
        fun clearSnapIndicator()
        fun strokesNear(left: Double, top: Double, right: Double, bottom: Double): List<Stroke>

        /** Whether [stroke] is on the frame shown and on a visible layer. */
        fun isVisible(stroke: Stroke): Boolean
    }

    /**
     * What the tool is showing: a dragged line (two points, open), a tapped stroke's outline
     * (closed when it's a closed shape: perimeter and area), or null. Not part of the document; it
     * stays until the next measurement or until another tool is chosen ([clear]).
     */
    var measurement: Measurement? by mutableStateOf(null)
        private set

    /** Starts a measuring drag at [worldPoint], snapped to a nearby stroke end or corner when object snapping is on. */
    fun begin(worldPoint: WorldPoint) {
        val start = host.snappedToObject(worldPoint) ?: worldPoint
        measurement = Measurement(listOf(start, start), closed = false)
    }

    fun move(worldPoint: WorldPoint) {
        val start = measurement?.points?.firstOrNull() ?: return
        measurement = Measurement(listOf(start, host.snappedToObject(worldPoint) ?: worldPoint), closed = false)
    }

    /**
     * Ends a measuring drag. A drag shorter than a tap measures what was tapped instead: the
     * smallest visible closed shape around the point (perimeter and area), else the nearest
     * visible line within reach (its length), else nothing.
     */
    fun end() {
        host.clearSnapIndicator()
        val line = measurement ?: return
        val (a, b) = line.points
        val scale = host.scale
        if (hypot(b.x - a.x, b.y - a.y) * scale > CanvasState.TAP_SLOP_SCREEN) return
        val reach = CanvasState.OBJECT_SNAP_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)
        val closeTolerance = CanvasState.FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        val near = host.strokesNear(a.x - reach, a.y - reach, a.x + reach, a.y + reach).filter { host.isVisible(it) && it.points.size >= 2 }
        val shape = host.strokes.filter { host.isVisible(it) && (it.filled || isClosedLoop(it.points, closeTolerance)) && polygonContains(it.points, a) }
            .minByOrNull { polygonArea(it.points) }
        measurement = when {
            shape != null -> Measurement(shape.points, closed = true)
            else -> near.minByOrNull { distanceToPolyline(it.points, a) }
                ?.takeIf { distanceToPolyline(it.points, a) <= reach }
                ?.let { Measurement(it.points, closed = false) }
        }
    }

    /** Drops what's shown; another tool was chosen. */
    fun clear() {
        measurement = null
    }
}
