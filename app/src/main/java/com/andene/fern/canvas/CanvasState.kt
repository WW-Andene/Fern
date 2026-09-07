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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class Tool { PEN, ERASER, SELECT, SHAPE, FILL }

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

        // How close (in screen pixels) a stroke's start/end points must be to count as a
        // "closed" shape for the FILL tool - same scale-dependent conversion as the eraser.
        private const val FILL_CLOSE_TOLERANCE_SCREEN = 24.0
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
    var activePenType by mutableStateOf(PenType.MARKER)
    var activeShapeKind by mutableStateOf(ShapeKind.LINE)

    /** The shape currently being dragged out (SHAPE tool), (start, snappedEnd) in world space. Null when none is in progress. */
    var shapePreview: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    private var shapeStart = WorldPoint.Zero

    /** Currently selected strokes (SELECT tool). Empty when nothing is selected. */
    val selection = mutableStateListOf<Stroke>()

    // The marquee rectangle currently being dragged out (SELECT tool), in world space, as
    // (dragStart, currentPoint) - not yet normalized into a left/top/right/bottom rect,
    // since the drag can go in any direction. Null when no marquee is in progress.
    var marqueeRect: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    private var isMovingSelection = false
    private var lastDragPoint: WorldPoint? = null

    // Scale/rotate gesture state. Both apply their transform fresh each frame from a
    // snapshot of each selected stroke's points taken at gesture start (gestureSnapshot),
    // rather than accumulating incremental deltas - which would drift over a long drag.
    private var gestureAnchor = WorldPoint.Zero // scale: opposite corner; rotate: pivot (centroid)
    private var gestureStartDistance = 0.0 // scale only
    private var gestureStartAngle = 0.0 // rotate only
    private var gestureSnapshot: Map<Stroke, List<WorldPoint>> = emptyMap()
    private var gestureSnapshotWidths: Map<Stroke, Double> = emptyMap()

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

    fun beginStroke(worldPoint: WorldPoint, pressure: Float = 1f, tilt: Float = 0f, orientation: Float = 0f) {
        redoStack.clear() // drawing something new invalidates redo history, standard editor semantics
        clearSelectionState() // avoid a stale selection referencing strokes another tool is about to change
        val stroke = Stroke(activeColor, activeWidthWorld / scale.coerceAtLeast(1e-300), activePenType)
        stroke.addPoint(worldPoint, pressure, tilt, orientation)
        currentStroke = stroke
        strokes.add(stroke)
    }

    fun extendStroke(worldPoint: WorldPoint, pressure: Float = 1f, tilt: Float = 0f, orientation: Float = 0f) {
        currentStroke?.addPoint(worldPoint, pressure, tilt, orientation)
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
        clearSelectionState() // avoid a stale selection referencing strokes this erase is about to split/remove
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

    fun beginShape(worldPoint: WorldPoint) {
        redoStack.clear()
        clearSelectionState()
        shapeStart = worldPoint
        shapePreview = worldPoint to worldPoint
    }

    fun continueShape(worldPoint: WorldPoint) {
        if (shapePreview == null) return
        shapePreview = shapeStart to snappedShapeEnd(activeShapeKind, shapeStart, worldPoint)
    }

    /** Commits the current shape preview as a new stroke. No-op if there was no actual drag (a zero-size shape). */
    fun endShape() {
        val preview = shapePreview ?: return
        shapePreview = null
        val (start, end) = preview
        if (start == end) return
        val stroke = Stroke(activeColor, activeWidthWorld / scale.coerceAtLeast(1e-300), activePenType)
        for (point in generateShapePoints(activeShapeKind, start, end)) stroke.addPoint(point)
        strokes.add(stroke)
        onChanged(strokes.toList())
    }

    /**
     * FILL tool: tapping inside a closed stroke fills its interior with [activeColor]. Since
     * this is a vector canvas (no pixel grid), "fill" means inserting a new filled-polygon
     * stroke using that boundary's exact points, positioned just beneath it in draw order so
     * the boundary's own outline still shows on top - not a raster flood fill, which
     * wouldn't make sense on an infinite canvas with no fixed resolution.
     *
     * Only strokes whose first and last point are close together (a "closed enough" loop) are
     * eligible; among all that actually contain [worldPoint], the smallest by area wins, so
     * tapping inside a small shape drawn inside a larger one fills the small one, matching
     * real fill-tool expectations. No-op if nothing qualifies.
     */
    fun fillAt(worldPoint: WorldPoint) {
        redoStack.clear()
        clearSelectionState()
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        var bestIndex = -1
        var bestArea = Double.POSITIVE_INFINITY
        for (index in strokes.indices) {
            val stroke = strokes[index]
            if (stroke.filled) continue
            val points = stroke.points
            if (!isClosedLoop(points, closeTolerance)) continue
            if (!polygonContains(points, worldPoint)) continue
            val area = polygonArea(points)
            if (area < bestArea) {
                bestArea = area
                bestIndex = index
            }
        }
        if (bestIndex == -1) return
        val boundary = strokes[bestIndex]
        val fillStroke = Stroke(activeColor, 0.0, PenType.MARKER, filled = true)
        for (point in boundary.points) fillStroke.addPoint(point)
        strokes.add(bestIndex, fillStroke)
        onChanged(strokes.toList())
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

    /** The current selection's combined bounding box, or null if nothing is selected. */
    fun selectionBounds(): Pair<WorldPoint, WorldPoint>? {
        if (selection.isEmpty()) return null
        val left = selection.minOf { it.minX }
        val right = selection.maxOf { it.maxX }
        val top = selection.minOf { it.minY }
        val bottom = selection.maxOf { it.maxY }
        return WorldPoint(left, top) to WorldPoint(right, bottom)
    }

    /**
     * Starts a uniform scale of the selection, anchored at the bounding box's top-left
     * corner (the corner opposite the bottom-right handle the caller hit-tested against) -
     * so dragging that handle resizes the selection the way dragging an image's corner
     * handle does, rather than scaling in place and drifting the selection's position.
     * No-op if nothing is selected.
     */
    fun beginScaleSelection(worldPoint: WorldPoint) {
        val bounds = selectionBounds() ?: return
        gestureAnchor = bounds.first
        gestureStartDistance = hypot(worldPoint.x - gestureAnchor.x, worldPoint.y - gestureAnchor.y).coerceAtLeast(1e-9)
        gestureSnapshot = selection.associateWith { it.points.toList() }
        gestureSnapshotWidths = selection.associateWith { it.widthWorld }
    }

    fun continueScaleSelection(worldPoint: WorldPoint) {
        if (gestureSnapshot.isEmpty()) return
        val distance = hypot(worldPoint.x - gestureAnchor.x, worldPoint.y - gestureAnchor.y).coerceAtLeast(1e-9)
        val factor = distance / gestureStartDistance
        for ((stroke, originalPoints) in gestureSnapshot) {
            stroke.setPoints(
                originalPoints.map { point ->
                    WorldPoint(
                        gestureAnchor.x + (point.x - gestureAnchor.x) * factor,
                        gestureAnchor.y + (point.y - gestureAnchor.y) * factor,
                    )
                }
            )
            gestureSnapshotWidths[stroke]?.let { stroke.setWidth(it * factor) }
        }
    }

    fun endScaleSelection() {
        gestureSnapshot = emptyMap()
        gestureSnapshotWidths = emptyMap()
        onChanged(strokes.toList())
    }

    /**
     * Starts rotating the selection around its bounding box's center. No-op if nothing is
     * selected.
     */
    fun beginRotateSelection(worldPoint: WorldPoint) {
        val bounds = selectionBounds() ?: return
        val (min, max) = bounds
        gestureAnchor = WorldPoint((min.x + max.x) / 2.0, (min.y + max.y) / 2.0)
        gestureStartAngle = atan2(worldPoint.y - gestureAnchor.y, worldPoint.x - gestureAnchor.x)
        gestureSnapshot = selection.associateWith { it.points.toList() }
    }

    fun continueRotateSelection(worldPoint: WorldPoint) {
        if (gestureSnapshot.isEmpty()) return
        val currentAngle = atan2(worldPoint.y - gestureAnchor.y, worldPoint.x - gestureAnchor.x)
        val delta = currentAngle - gestureStartAngle
        val cosDelta = cos(delta)
        val sinDelta = sin(delta)
        for ((stroke, originalPoints) in gestureSnapshot) {
            stroke.setPoints(
                originalPoints.map { point ->
                    val dx = point.x - gestureAnchor.x
                    val dy = point.y - gestureAnchor.y
                    WorldPoint(
                        gestureAnchor.x + dx * cosDelta - dy * sinDelta,
                        gestureAnchor.y + dx * sinDelta + dy * cosDelta,
                    )
                }
            )
        }
    }

    fun endRotateSelection() {
        gestureSnapshot = emptyMap()
        onChanged(strokes.toList())
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
            val copy = Stroke(original.color, original.widthWorld, original.penType, original.filled)
            for (point in original.points) copy.addPoint(point + nudge)
            copy
        }
        strokes.addAll(duplicates)
        selection.clear()
        selection.addAll(duplicates)
        onChanged(strokes.toList())
    }

    /** Clears the selection and any in-progress scale/rotate snapshot together, so neither can outlive the strokes it references. */
    private fun clearSelectionState() {
        selection.clear()
        gestureSnapshot = emptyMap()
        gestureSnapshotWidths = emptyMap()
    }

    private fun pointInsideSelectionBounds(point: WorldPoint): Boolean {
        val (min, max) = selectionBounds() ?: return false
        return point.x in min.x..max.x && point.y in min.y..max.y
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            clearSelectionState()
            redoStack.add(strokes.removeAt(strokes.size - 1))
            onChanged(strokes.toList())
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            clearSelectionState()
            strokes.add(redoStack.removeAt(redoStack.size - 1))
            onChanged(strokes.toList())
        }
    }

    /** Replaces all strokes with [loaded] (e.g. from [CanvasStorage.load] on app start). */
    fun loadStrokes(loaded: List<Stroke>) {
        strokes.clear()
        redoStack.clear()
        clearSelectionState()
        strokes.addAll(loaded)
    }

    fun clear() {
        strokes.clear()
        redoStack.clear()
        clearSelectionState()
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

    /** Jumps the camera to [world] at [targetScale] (e.g. restoring a saved pin). */
    fun jumpTo(world: WorldPoint, targetScale: Double) {
        scale = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
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
