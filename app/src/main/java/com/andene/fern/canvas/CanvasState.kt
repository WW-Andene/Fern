package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

enum class Tool { PEN, ERASER, SELECT }

/**
 * Camera + document model for the infinite canvas.
 *
 * ## Why this actually gets you unbounded pan/zoom
 *
 * Compose's [Offset] is Float, which only has ~7 significant decimal digits — nowhere near
 * enough for "infinite". This state instead keeps [panWorld] and [scale] as Double
 * (~15-17 significant digits), and — critically — uses a **floating origin**: whenever the
 * camera has drifted far from world-space (0,0), [rebaseIfNeeded] re-anchors the origin to
 * the camera by subtracting [panWorld] from every stored point and resetting [panWorld] to
 * zero.
 *
 * That matters because Double precision is *relative to magnitude*: a Double near
 * 10,000,000 has much coarser precision than one near 0. Without rebasing, panning far
 * enough would eventually make `world - panWorld` catastrophically lose precision, same
 * as with Float, just later. With rebasing, all stored coordinates and the pan offset are
 * always kept close to zero, so that subtraction never loses precision no matter how far
 * you've panned — pan range is unbounded for any realistic drawing session.
 *
 * Zoom depth is carried entirely by [scale], which never needs rebasing: Double covers
 * roughly 300 orders of magnitude (~4.9e-324 to ~1.8e308) with full relative precision at
 * every level, since it's a pure multiplier applied after the (already-small,
 * already-precise) rebased coordinates. [MIN_SCALE]/[MAX_SCALE] leave enormous headroom
 * below that ceiling purely to avoid float overflow/underflow at the very last
 * Double->Float conversion when actually drawing to screen — for any human drawing task
 * this is indistinguishable from literally infinite zoom.
 *
 * @param onChanged invoked with a snapshot of [strokes] after every completed mutation
 *   (a finished stroke, an undo, a clear) — not on every point mid-stroke. Intended for
 *   driving autosave; the caller decides how/when to actually persist the snapshot.
 */
class CanvasState(private val onChanged: (List<Stroke>) -> Unit = {}) {
    companion object {
        // Effectively unbounded: leaves ~250 orders of magnitude of headroom on both sides
        // of Double's range purely as a safety margin, not a meaningful practical limit.
        const val MIN_SCALE = 1e-250
        const val MAX_SCALE = 1e250

        // World-space distance the camera can drift from the current origin before we
        // re-anchor. Large enough that rebasing is rare (not a per-frame cost), small
        // enough that precision near the origin never meaningfully degrades.
        private const val REBASE_THRESHOLD = 65536.0

        // Eraser radius in screen pixels (on the §7 scale - Secondary tier), converted to
        // world units by the same scale-dependent logic as pen width, so the eraser covers
        // a consistent on-screen area regardless of zoom level.
        private const val ERASER_RADIUS_SCREEN = 24.0
    }

    // World-space point currently at the center of the screen.
    var panWorld by mutableStateOf(WorldPoint.Zero)
        private set

    var scale by mutableDoubleStateOf(1.0)
        private set

    /**
     * The drawing surface's current on-screen size in pixels, kept up to date by
     * [DrawingCanvas] every frame. Lets [zoomToFit] and the minimap compute what the
     * viewport covers without every caller having to thread the Canvas size through
     * themselves.
     */
    var viewportSize by mutableStateOf(Size.Zero)

    val strokes = mutableStateListOf<Stroke>()

    var activeColor by mutableStateOf(Color(0xFF1B1B1B))
    var activeWidthWorld by mutableDoubleStateOf(4.0)
    var activeTool by mutableStateOf(Tool.PEN)

    /** Currently selected strokes (SELECT tool). Empty when nothing is selected. */
    val selection = mutableStateListOf<Stroke>()

    // The marquee rectangle currently being dragged out (SELECT tool), in world space, as
    // (dragStart, currentPoint) - not yet normalized into a left/top/right/bottom rect,
    // since the drag can go in any direction. Null when no marquee is in progress.
    var marqueeRect: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    private var isMovingSelection = false
    private var lastDragPoint: WorldPoint? = null

    private var currentStroke: Stroke? = null

    // Strokes popped by undo, in the order they can be redone (last popped, first redone).
    // Not exposed as Compose state: nothing currently renders redo-availability, so this
    // doesn't need to trigger recomposition on its own (§5.2 - no speculative UI hooks).
    private val redoStack = mutableListOf<Stroke>()

    fun worldToScreen(world: WorldPoint, screenCenter: Offset): Offset {
        val relative = world - panWorld
        return Offset(
            x = screenCenter.x + (relative.x * scale).toFloat(),
            y = screenCenter.y + (relative.y * scale).toFloat(),
        )
    }

    fun screenToWorld(screen: Offset, screenCenter: Offset): WorldPoint {
        return WorldPoint(
            x = (screen.x - screenCenter.x).toDouble() / scale + panWorld.x,
            y = (screen.y - screenCenter.y).toDouble() / scale + panWorld.y,
        )
    }

    fun beginStroke(worldPoint: WorldPoint) {
        redoStack.clear() // drawing something new invalidates redo history, standard editor semantics
        selection.clear() // avoid a stale selection referencing strokes another tool is about to change
        val stroke = Stroke(activeColor, activeWidthWorld / scale.coerceAtLeast(1e-300))
        stroke.addPoint(worldPoint)
        currentStroke = stroke
        strokes.add(stroke)
    }

    fun extendStroke(worldPoint: WorldPoint) {
        currentStroke?.addPoint(worldPoint)
    }

    fun endStroke() {
        currentStroke = null
        rebaseIfNeeded()
        onChanged(strokes.toList())
    }

    /** World-space radius of the eraser at the current zoom level. */
    private val eraserRadiusWorld: Double
        get() = ERASER_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)

    fun beginErase(worldPoint: WorldPoint) {
        redoStack.clear()
        selection.clear() // avoid a stale selection referencing strokes this erase is about to split/remove
        eraseAt(worldPoint)
    }

    fun continueErase(worldPoint: WorldPoint) {
        eraseAt(worldPoint)
    }

    fun endErase() {
        onChanged(strokes.toList())
    }

    private fun eraseAt(point: WorldPoint) {
        val radius = eraserRadiusWorld
        for (stroke in strokes.toList()) {
            if (!stroke.intersects(point.x - radius, point.y - radius, point.x + radius, point.y + radius)) continue
            val pieces = stroke.eraseNear(point, radius) ?: continue
            val index = strokes.indexOf(stroke)
            if (index == -1) continue
            strokes.removeAt(index)
            strokes.addAll(index, pieces)
        }
    }

    /**
     * SELECT tool, pointer-down: starts moving the current selection if [worldPoint] falls
     * inside its combined bounds, otherwise starts a new marquee drag (replacing any
     * existing selection once the marquee is released).
     */
    fun beginSelectGesture(worldPoint: WorldPoint) {
        redoStack.clear()
        if (selection.isNotEmpty() && pointInsideSelectionBounds(worldPoint)) {
            isMovingSelection = true
            lastDragPoint = worldPoint
        } else {
            isMovingSelection = false
            selection.clear()
            marqueeRect = worldPoint to worldPoint
        }
    }

    fun continueSelectGesture(worldPoint: WorldPoint) {
        if (isMovingSelection) {
            val last = lastDragPoint ?: return
            val delta = worldPoint - last
            for (stroke in selection) stroke.shiftBy(WorldPoint(-delta.x, -delta.y))
            lastDragPoint = worldPoint
        } else {
            val start = marqueeRect?.first ?: return
            marqueeRect = start to worldPoint
        }
    }

    fun endSelectGesture() {
        if (isMovingSelection) {
            isMovingSelection = false
            lastDragPoint = null
            onChanged(strokes.toList())
            return
        }
        val rect = marqueeRect
        marqueeRect = null
        if (rect == null) return
        val (start, end) = rect
        val left = minOf(start.x, end.x)
        val right = maxOf(start.x, end.x)
        val top = minOf(start.y, end.y)
        val bottom = maxOf(start.y, end.y)
        selection.addAll(strokes.filter { it.intersects(left, top, right, bottom) })
    }

    /** Deletes every currently selected stroke. */
    fun deleteSelection() {
        if (selection.isEmpty()) return
        strokes.removeAll(selection.toSet())
        selection.clear()
        onChanged(strokes.toList())
    }

    /** Duplicates every currently selected stroke, offset slightly so the copy is visible, and selects the copies. */
    fun duplicateSelection() {
        if (selection.isEmpty()) return
        val nudge = WorldPoint(20.0 / scale.coerceAtLeast(1e-300), 20.0 / scale.coerceAtLeast(1e-300))
        val duplicates = selection.map { original ->
            val copy = Stroke(original.color, original.widthWorld)
            for (point in original.points) copy.addPoint(point + nudge)
            copy
        }
        strokes.addAll(duplicates)
        selection.clear()
        selection.addAll(duplicates)
        onChanged(strokes.toList())
    }

    private fun pointInsideSelectionBounds(point: WorldPoint): Boolean {
        val left = selection.minOf { it.minX }
        val right = selection.maxOf { it.maxX }
        val top = selection.minOf { it.minY }
        val bottom = selection.maxOf { it.maxY }
        return point.x in left..right && point.y in top..bottom
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            selection.clear()
            redoStack.add(strokes.removeAt(strokes.size - 1))
            onChanged(strokes.toList())
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            selection.clear()
            strokes.add(redoStack.removeAt(redoStack.size - 1))
            onChanged(strokes.toList())
        }
    }

    /** Replaces all strokes with [loaded] (e.g. from [CanvasStorage.load] on app start). */
    fun loadStrokes(loaded: List<Stroke>) {
        strokes.clear()
        redoStack.clear()
        selection.clear()
        strokes.addAll(loaded)
    }

    fun clear() {
        strokes.clear()
        redoStack.clear()
        selection.clear()
        onChanged(strokes.toList())
    }

    /** Pan by a screen-space delta (drag). Only call while no stroke is in progress. */
    fun panByScreenDelta(delta: Offset) {
        panWorld -= WorldPoint(delta.x.toDouble(), delta.y.toDouble()) / scale
        rebaseIfNeeded()
    }

    /**
     * Zoom by [factor] around the world point currently under [screenFocus].
     * Only call while no stroke is in progress.
     */
    fun zoomAround(screenFocus: Offset, screenCenter: Offset, factor: Double) {
        val worldFocusBefore = screenToWorld(screenFocus, screenCenter)
        scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val worldFocusAfter = screenToWorld(screenFocus, screenCenter)
        panWorld += (worldFocusBefore - worldFocusAfter)
        rebaseIfNeeded()
    }

    fun resetView() {
        panWorld = WorldPoint.Zero
        scale = 1.0
    }

    /** The combined bounding box of every stroke, or null if the canvas is empty. */
    fun contentBounds(): Pair<WorldPoint, WorldPoint>? {
        if (strokes.isEmpty()) return null
        val minX = strokes.minOf { it.minX }
        val minY = strokes.minOf { it.minY }
        val maxX = strokes.maxOf { it.maxX }
        val maxY = strokes.maxOf { it.maxY }
        return WorldPoint(minX, minY) to WorldPoint(maxX, maxY)
    }

    /** Recenters the camera on [world] without changing zoom. */
    fun panTo(world: WorldPoint) {
        panWorld = world
        rebaseIfNeeded()
    }

    /** Frames all content in the current viewport, with a margin. No-op on an empty canvas. */
    fun zoomToFit() {
        val bounds = contentBounds() ?: return
        val viewport = viewportSize
        if (viewport.width <= 0f || viewport.height <= 0f) return
        val (min, max) = bounds
        val contentWidth = (max.x - min.x).coerceAtLeast(1e-6)
        val contentHeight = (max.y - min.y).coerceAtLeast(1e-6)
        // 0.8 margin: content fills 80% of the viewport, leaving breathing room at the edges.
        val fitScaleX = viewport.width.toDouble() * 0.8 / contentWidth
        val fitScaleY = viewport.height.toDouble() * 0.8 / contentHeight
        scale = minOf(fitScaleX, fitScaleY).coerceIn(MIN_SCALE, MAX_SCALE)
        panWorld = WorldPoint((min.x + max.x) / 2.0, (min.y + max.y) / 2.0)
        rebaseIfNeeded()
    }

    /**
     * Re-anchors world-space (0,0) to wherever the camera currently is, so [panWorld]
     * (and every stored stroke coordinate) stays close to zero. Safe to call any time no
     * stroke is actively being drawn — call it after gestures settle, not mid-stroke,
     * since it mutates the same point list a live stroke may still be appending to.
     */
    private fun rebaseIfNeeded() {
        if (abs(panWorld.x) < REBASE_THRESHOLD && abs(panWorld.y) < REBASE_THRESHOLD) return
        val offset = panWorld
        for (stroke in strokes) {
            stroke.shiftBy(offset)
        }
        panWorld = WorldPoint.Zero
    }
}
