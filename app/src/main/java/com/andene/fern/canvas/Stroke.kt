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
 *   nib angle (a real stylus's tilt orientation when available, otherwise a fixed 45°),
 *   simulating a flat calligraphy nib.
 */
enum class PenType { MARKER, PENCIL, HIGHLIGHTER, CALLIGRAPHY }

/**
 * A single freehand stroke, stored entirely in world space (i.e. independent of
 * the current pan/zoom). Points are appended live while the user is drawing.
 *
 * All coordinates are Double, and get shifted in place whenever [CanvasState] rebases
 * its floating origin — see the class doc there for why that's what makes pan/zoom
 * effectively unbounded.
 *
 * Each point also carries stylus [pressures]/[tilts]/[orientations] (parallel arrays,
 * same length and index correspondence as [points]) captured from the raw `MotionEvent`
 * at draw time - see `DrawingCanvas`'s `pointerInteropFilter` side-channel. Finger input (or
 * any non-stylus tool) reports pressure 1.0 and tilt 0.0, which is indistinguishable from
 * "no data" and renders exactly as before pressure/tilt support existed.
 */
class Stroke(
    val color: Color,
    widthWorld: Double,
    val penType: PenType = PenType.MARKER,
    /** True for a FILL-tool result: a solid polygon (drawn with [DrawingCanvas]'s fill path, not stroked) rather than an outline. */
    val filled: Boolean = false,
) {
    /** Mutable so a SELECT-tool scale gesture can resize the stroke proportionally with its geometry. */
    var widthWorld: Double = widthWorld
        private set

    private val _points = mutableListOf<WorldPoint>()
    val points: List<WorldPoint> get() = _points

    // Parallel to _points: pressure (0..1, 1.0 = no pressure data), tilt (radians, 0 =
    // perpendicular to the screen / no tilt data), and orientation (radians, the compass
    // direction the stylus is tilted toward) at the moment each point was captured.
    private val _pressures = mutableListOf<Float>()
    val pressures: List<Float> get() = _pressures
    private val _tilts = mutableListOf<Float>()
    val tilts: List<Float> get() = _tilts
    private val _orientations = mutableListOf<Float>()
    val orientations: List<Float> get() = _orientations

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

    fun addPoint(point: WorldPoint, pressure: Float = 1f, tilt: Float = 0f, orientation: Float = 0f) {
        _points.add(point)
        _pressures.add(pressure)
        _tilts.add(tilt)
        _orientations.add(orientation)
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
     * drift over a long drag). [newPoints] must be the same length as the current [points]
     * (a transform moves points, it never adds/removes them), so [pressures]/[tilts]/
     * [orientations] stay correctly paired by index without needing to be passed in too.
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
     * inside the circle is removed, and the remaining points (with their pressure/tilt/
     * orientation) are split back into separate pieces wherever a removal broke the
     * stroke's continuity (each piece keeps this stroke's color/width/pen type). A run left
     * with only a single surviving point is dropped rather than kept as a degenerate
     * one-point piece.
     *
     * Returns null if no point was inside the radius (caller leaves the original stroke
     * untouched), otherwise the list of surviving pieces — which is empty if erasing
     * removed the stroke entirely.
     */
    fun eraseNear(center: WorldPoint, radius: Double): List<Stroke>? {
        val radiusSq = radius * radius
        var anyRemoved = false
        val runs = mutableListOf<MutableList<Int>>() // indices into _points/_pressures/_tilts/_orientations
        var current: MutableList<Int>? = null
        for (index in points.indices) {
            val point = points[index]
            val dx = point.x - center.x
            val dy = point.y - center.y
            val inside = dx * dx + dy * dy <= radiusSq
            if (inside) {
                anyRemoved = true
                current = null
            } else {
                val run = current ?: mutableListOf<Int>().also {
                    current = it
                    runs.add(it)
                }
                run.add(index)
            }
        }
        if (!anyRemoved) return null
        return runs.filter { it.size >= 2 }.map { run ->
            val piece = Stroke(color, widthWorld, penType, filled)
            for (index in run) piece.addPoint(points[index], pressures[index], tilts[index], orientations[index])
            piece
        }
    }
}
