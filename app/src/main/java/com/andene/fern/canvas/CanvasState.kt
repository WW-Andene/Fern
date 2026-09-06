package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Camera + document model for the infinite canvas.
 *
 * World space is unbounded in principle. Practically it is bounded by Float precision:
 * scale is clamped to [MIN_SCALE, MAX_SCALE], giving roughly 9 orders of magnitude of
 * zoom range (comparable to zooming from a full city map down to individual letters),
 * which is the same trick apps like Endless Paper use rather than true arbitrary-precision
 * zoom.
 */
class CanvasState {
    companion object {
        const val MIN_SCALE = 0.001f
        const val MAX_SCALE = 4096f
    }

    // World-space point currently at the center of the screen.
    var panWorld by mutableStateOf(Offset.Zero)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    val strokes = mutableStateListOf<Stroke>()

    var activeColor by mutableStateOf(Color(0xFF1B1B1B))
    var activeWidthWorld by mutableFloatStateOf(4f)

    private var currentStroke: Stroke? = null

    fun worldToScreen(world: Offset, screenCenter: Offset): Offset {
        return (world - panWorld) * scale + screenCenter
    }

    fun screenToWorld(screen: Offset, screenCenter: Offset): Offset {
        return (screen - screenCenter) / scale + panWorld
    }

    fun beginStroke(worldPoint: Offset) {
        val stroke = Stroke(activeColor, activeWidthWorld / scale.coerceAtLeast(1e-6f))
        stroke.addPoint(worldPoint)
        currentStroke = stroke
        strokes.add(stroke)
    }

    fun extendStroke(worldPoint: Offset) {
        currentStroke?.addPoint(worldPoint)
    }

    fun endStroke() {
        currentStroke = null
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
    }

    fun clear() {
        strokes.clear()
    }

    /** Pan by a screen-space delta (drag). */
    fun panByScreenDelta(delta: Offset) {
        panWorld -= delta / scale
    }

    /** Zoom by [factor] around the world point currently under [screenFocus]. */
    fun zoomAround(screenFocus: Offset, screenCenter: Offset, factor: Float) {
        val worldFocusBefore = screenToWorld(screenFocus, screenCenter)
        scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val worldFocusAfter = screenToWorld(screenFocus, screenCenter)
        panWorld += (worldFocusBefore - worldFocusAfter)
    }

    fun resetView() {
        panWorld = Offset.Zero
        scale = 1f
    }
}
