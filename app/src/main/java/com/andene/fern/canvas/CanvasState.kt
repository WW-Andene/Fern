package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

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
    }

    // World-space point currently at the center of the screen.
    var panWorld by mutableStateOf(WorldPoint.Zero)
        private set

    var scale by mutableDoubleStateOf(1.0)
        private set

    val strokes = mutableStateListOf<Stroke>()

    var activeColor by mutableStateOf(Color(0xFF1B1B1B))
    var activeWidthWorld by mutableDoubleStateOf(4.0)

    private var currentStroke: Stroke? = null

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

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            onChanged(strokes.toList())
        }
    }

    /** Replaces all strokes with [loaded] (e.g. from [CanvasStorage.load] on app start). */
    fun loadStrokes(loaded: List<Stroke>) {
        strokes.clear()
        strokes.addAll(loaded)
    }

    fun clear() {
        strokes.clear()
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
