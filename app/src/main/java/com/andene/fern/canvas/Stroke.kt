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
 * - [AIRBRUSH]: a soft spray of fine dots around the path, denser at the centre.
 * - [CRAYON]: a waxy line broken up by paper grain, letting the paper show through.
 * - [PIXEL]: pixel art - fills whole square cells of a grid whose cell size is the stroke
 *   width, with no gaps between cells however fast the pen moves.
 * - [STAMP]: a [StampBrush] tip repeated along the path, with the stroke's own spacing,
 *   scatter, size and angle settings (see [Stroke.brush]).
 */
enum class PenType { MARKER, PENCIL, HIGHLIGHTER, CALLIGRAPHY, AIRBRUSH, CRAYON, PIXEL, STAMP }

/**
 * How a stroke composites with whatever is already drawn beneath it, mirroring the standard
 * blend-mode vocabulary found in raster paint tools:
 * - [NORMAL]: draws over what's beneath, the default.
 * - [MULTIPLY]: darkens what's beneath, letting strokes underneath still show through -
 *   useful for shading/highlighting over existing ink.
 * - [SCREEN]: lightens what's beneath - the inverse of multiply.
 */
enum class StrokeBlendMode { NORMAL, MULTIPLY, SCREEN }

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
    /** How this stroke composites with content beneath it - see [StrokeBlendMode]. */
    val blendMode: StrokeBlendMode = StrokeBlendMode.NORMAL,
    /** The [Layer] this stroke is drawn on. */
    val layerId: String = DEFAULT_LAYER_ID,
    /** Animation frame (0-based) this stroke belongs to; only frame 0 exists outside animation mode. */
    val animFrame: Int = 0,
    /** Drawn as a dashed line (dash 3x the width, gap 2x) - see DrawingCanvas.drawStroke. */
    val dashed: Boolean = false,
    /** For a fill: fades from [color] at the top to transparent at the bottom instead of being solid. */
    val fadeFill: Boolean = false,
    /** For a fill: a comic screentone (a grid of dots in [color]) instead of a solid colour. */
    val screentone: Boolean = false,
    /** The brush a [PenType.STAMP] stroke was drawn with; null for every other pen. */
    val brush: StampBrush? = null,
    /** For a fill: a two-colour gradient from [color] instead of a solid colour. */
    val gradient: FillGradient? = null,
    /** Strokes sharing a group id are selected, moved and edited together; null when not grouped. */
    val groupId: String? = null,
) {
    /** Mutable so a SELECT-tool scale gesture can resize the stroke proportionally with its geometry. */
    var widthWorld: Double = widthWorld
        private set

    // Point data lives in growable primitive arrays, not lists of objects: a WorldPoint plus
    // three boxed Floats cost ~106 bytes of heap per point, which a 100k-stroke document can't
    // afford on a phone; this costs 28 (two Doubles, three Floats). Parallel by index:
    // pressure (0..1, 1.0 = no pressure data), tilt (radians, 0 = perpendicular to the screen /
    // no tilt data), and orientation (radians, the compass direction the stylus is tilted
    // toward) at the moment each point was captured.
    private var xs = DoubleArray(INITIAL_CAPACITY)
    private var ys = DoubleArray(INITIAL_CAPACITY)
    private var pressureData = FloatArray(INITIAL_CAPACITY)
    private var tiltData = FloatArray(INITIAL_CAPACITY)
    private var orientationData = FloatArray(INITIAL_CAPACITY)
    private var size = 0

    /** A live read-only view of the points; each element is created on access. */
    val points: List<WorldPoint> = object : AbstractList<WorldPoint>() {
        override val size: Int get() = this@Stroke.size
        override fun get(index: Int): WorldPoint {
            checkIndex(index)
            return WorldPoint(xs[index], ys[index])
        }
    }

    /** Live views of the stylus data; per-frame code should prefer [pressureAt] and friends, which don't box. */
    val pressures: List<Float> = floatView { pressureData }
    val tilts: List<Float> = floatView { tiltData }
    val orientations: List<Float> = floatView { orientationData }

    fun pressureAt(index: Int): Float { checkIndex(index); return pressureData[index] }
    fun tiltAt(index: Int): Float { checkIndex(index); return tiltData[index] }
    fun orientationAt(index: Int): Float { checkIndex(index); return orientationData[index] }

    private fun floatView(data: () -> FloatArray): List<Float> = object : AbstractList<Float>() {
        override val size: Int get() = this@Stroke.size
        override fun get(index: Int): Float {
            checkIndex(index)
            return data()[index]
        }
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index $index, size $size")
    }

    /**
     * Bumped on every mutation ([addPoint], [shiftBy]). The point arrays themselves are plain
     * arrays, not something Compose's snapshot system can see — so without this,
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
        if (size == xs.size) grow()
        xs[size] = point.x
        ys[size] = point.y
        pressureData[size] = pressure
        tiltData[size] = tilt
        orientationData[size] = orientation
        size++
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
        for (i in 0 until size) {
            xs[i] -= offset.x
            ys[i] -= offset.y
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
        require(newPoints.size == size) { "setPoints moves points; it can't change their number" }
        // Read everything before writing, in case newPoints is a view of this stroke.
        val newXs = DoubleArray(xs.size)
        val newYs = DoubleArray(ys.size)
        for (i in newPoints.indices) {
            newXs[i] = newPoints[i].x
            newYs[i] = newPoints[i].y
        }
        xs = newXs
        ys = newYs
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

    /** A deep copy drawn in [newColor] instead; colour is immutable on a stroke, so recolouring replaces it. */
    fun withColor(newColor: Color): Stroke {
        return restyled(newColor, layerId)
    }

    /** A deep copy moved onto [newLayerId], e.g. when merging a layer down. */
    fun onLayer(newLayerId: String): Stroke = restyled(color, newLayerId)

    /** A deep copy moved to animation frame [frame], e.g. when frames are inserted or deleted. */
    fun onAnimFrame(frame: Int): Stroke = restyled(color, layerId, frame)

    /** A deep copy in group [newGroupId] (null = ungrouped). */
    fun inGroup(newGroupId: String?): Stroke = restyled(color, layerId, newGroupId = newGroupId)

    private fun restyled(newColor: Color, newLayerId: String, newFrame: Int = animFrame, newGroupId: String? = groupId): Stroke {
        val copy = Stroke(newColor, widthWorld, penType, filled, blendMode, newLayerId, newFrame, dashed, fadeFill, screentone, brush, gradient, newGroupId)
        for (i in 0 until size) copy.addPoint(WorldPoint(xs[i], ys[i]), pressureData[i], tiltData[i], orientationData[i])
        return copy
    }

    /** An independent copy (own point lists), so an undo snapshot isn't changed by later edits to this stroke. */
    fun deepCopy(): Stroke = withColor(color)

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
        return eraseWhere { i ->
            val dx = xs[i] - center.x
            val dy = ys[i] - center.y
            dx * dx + dy * dy <= radiusSq
        }
    }

    /** [eraseNear] for a lasso: removes the points inside [polygon] (see [polygonContains]). */
    fun eraseInside(polygon: List<WorldPoint>): List<Stroke>? =
        eraseWhere { i -> polygonContains(polygon, WorldPoint(xs[i], ys[i])) }

    /**
     * Removes every point [erased] selects and splits the survivors into pieces wherever a removal
     * broke the stroke's continuity; each piece keeps this stroke's style, and a piece left with
     * a single point is dropped. Returns null if nothing was erased, otherwise the surviving
     * pieces (empty if the whole stroke went).
     */
    private inline fun eraseWhere(erased: (Int) -> Boolean): List<Stroke>? {
        var anyRemoved = false
        val runs = mutableListOf<MutableList<Int>>() // point indices
        var current: MutableList<Int>? = null
        for (index in 0 until size) {
            if (erased(index)) {
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
            val piece = Stroke(color, widthWorld, penType, filled, blendMode, layerId, animFrame, dashed, fadeFill, screentone, brush, gradient, groupId)
            for (index in run) piece.addPoint(WorldPoint(xs[index], ys[index]), pressureData[index], tiltData[index], orientationData[index])
            piece
        }
    }

    private fun grow() {
        val capacity = xs.size * 2
        xs = xs.copyOf(capacity)
        ys = ys.copyOf(capacity)
        pressureData = pressureData.copyOf(capacity)
        tiltData = tiltData.copyOf(capacity)
        orientationData = orientationData.copyOf(capacity)
    }

    private companion object {
        // Most strokes are short; doubling from 8 keeps the slack small for them.
        const val INITIAL_CAPACITY = 8
    }
}
