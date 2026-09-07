package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * How a stroke's points are rendered. Each is a genuinely different visual treatment
 * (see `DrawingCanvas`'s render loop), not just a label on the same line style:
 * - [MARKER]: solid, full opacity, round caps - the default.
 * - [PENCIL]: thinner and slightly translucent.
 * - [HIGHLIGHTER]: wide and strongly translucent, with flat (square) caps.
 * - [CALLIGRAPHY]: width varies along the stroke based on drawing direction relative to a
 *   fixed nib angle, simulating a flat calligraphy nib.
 */
enum class PenType { MARKER, PENCIL, HIGHLIGHTER, CALLIGRAPHY }

/**
 * A single freehand stroke, stored entirely in world space (i.e. independent of
 * the current pan/zoom). Points are appended live while the user is drawing.
 *
 * All coordinates are Double, and get shifted in place whenever [CanvasState] rebases
 * its floating origin — see the class doc there for why that's what makes pan/zoom
 * effectively unbounded.
 */
class Stroke(
    val color: Color,
    widthWorld: Double,
    val penType: PenType = PenType.MARKER,
) {
    /** Mutable so a SELECT-tool scale gesture can resize the stroke proportionally with its geometry. */
    var widthWorld: Double = widthWorld
        private set

    private val _points = mutableListOf<WorldPoint>()
    val points: List<WorldPoint> get() = _points

    /**
     * Bumped on every mutation ([addPoint], [shiftBy]). [_points] itself is a plain
     * `MutableList`, not something Compose's snapshot system can see — so without this,
     * appending points while a stroke is being drawn wouldn't invalidate the `Canvas`
     * draw phase at all, and the in-progress stroke would only actually render on the
     * next *unrelated* redraw (e.g. the next pan/zoom, since panWorld/scale are
     * observed). Reading [revision] inside the draw loop is what makes new points show
     * up immediately, point by point, as the finger moves.
     */
    var revision by mutableIntStateOf(0)
        private set

    // Cached bounding box in world space, expanded as points are added.
    var minX: Double = Double.POSITIVE_INFINITY; private set
    var minY: Double = Double.POSITIVE_INFINITY; private set
    var maxX: Double = Double.NEGATIVE_INFINITY; private set
    var maxY: Double = Double.NEGATIVE_INFINITY; private set

    fun addPoint(point: WorldPoint) {
        _points.add(point)
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
        revision++
    }

    /** True if this stroke's bounding box (padded by its width) intersects the given world-space rect. */
    fun intersects(left: Double, top: Double, right: Double, bottom: Double): Boolean {
        if (points.isEmpty()) return false
        val pad = widthWorld
        return !(maxX + pad < left || minX - pad > right || maxY + pad < top || minY - pad > bottom)
    }

    /** Shifts every point (and the cached bounds) by [-offset], used when re-anchoring the world origin. */
    fun shiftBy(offset: WorldPoint) {
        for (i in _points.indices) {
            _points[i] -= offset
        }
        minX -= offset.x; maxX -= offset.x
        minY -= offset.y; maxY -= offset.y
        revision++
    }

    /**
     * Replaces every point wholesale and recomputes the bounding box, for a SELECT-tool
     * scale/rotate gesture (which recomputes each point fresh from a snapshot taken at
     * gesture start, rather than accumulating incremental deltas frame to frame - avoiding
     * drift over a long drag).
     */
    fun setPoints(newPoints: List<WorldPoint>) {
        _points.clear()
        _points.addAll(newPoints)
        minX = Double.POSITIVE_INFINITY
        minY = Double.POSITIVE_INFINITY
        maxX = Double.NEGATIVE_INFINITY
        maxY = Double.NEGATIVE_INFINITY
        for (point in newPoints) {
            if (point.x < minX) minX = point.x
            if (point.y < minY) minY = point.y
            if (point.x > maxX) maxX = point.x
            if (point.y > maxY) maxY = point.y
        }
        revision++
    }

    /** Sets this stroke's width directly, for a SELECT-tool scale gesture. */
    fun setWidth(newWidth: Double) {
        widthWorld = newWidth
        revision++
    }

    /**
     * Erases the part of this stroke within [radius] of [center]: every point that falls
     * inside the circle is removed, and the remaining points are split back into separate
     * pieces wherever a removal broke the stroke's continuity (each piece keeps this
     * stroke's color/width). A run left with only a single surviving point is dropped
     * rather than kept as a degenerate one-point piece.
     *
     * Returns null if no point was inside the radius (caller leaves the original stroke
     * untouched), otherwise the list of surviving pieces — which is empty if erasing
     * removed the stroke entirely.
     */
    fun eraseNear(center: WorldPoint, radius: Double): List<Stroke>? {
        val radiusSq = radius * radius
        var anyRemoved = false
        val runs = mutableListOf<MutableList<WorldPoint>>()
        var current: MutableList<WorldPoint>? = null
        for (point in points) {
            val dx = point.x - center.x
            val dy = point.y - center.y
            val inside = dx * dx + dy * dy <= radiusSq
            if (inside) {
                anyRemoved = true
                current = null
            } else {
                val run = current ?: mutableListOf<WorldPoint>().also {
                    current = it
                    runs.add(it)
                }
                run.add(point)
            }
        }
        if (!anyRemoved) return null
        return runs.filter { it.size >= 2 }.map { run ->
            val piece = Stroke(color, widthWorld, penType)
            for (point in run) piece.addPoint(point)
            piece
        }
    }
}
