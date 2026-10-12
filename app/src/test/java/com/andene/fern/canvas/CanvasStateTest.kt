package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CanvasStateTest {
    private fun drawLine(state: CanvasState, from: WorldPoint, to: WorldPoint) {
        state.beginStroke(from)
        state.extendStroke(WorldPoint((from.x + to.x) / 2, (from.y + to.y) / 2))
        state.extendStroke(to)
        state.endStroke()
    }

    @Test
    fun undoRedoStroke() {
        val state = CanvasState()
        drawLine(state, WorldPoint(0.0, 0.0), WorldPoint(100.0, 37.0))
        assertEquals(1, state.strokes.size)
        state.undo()
        assertEquals(0, state.strokes.size)
        state.redo()
        assertEquals(1, state.strokes.size)
    }

    @Test
    fun clearIsUndoable() {
        val state = CanvasState()
        drawLine(state, WorldPoint(0.0, 0.0), WorldPoint(100.0, 37.0))
        state.clear()
        assertEquals(0, state.strokes.size)
        state.undo()
        assertEquals(1, state.strokes.size)
    }

    @Test
    fun zoomKeepsFocusPointFixed() {
        val state = CanvasState()
        val center = Offset(500f, 500f)
        val focus = Offset(700f, 300f)
        val before = state.screenToWorld(focus, center)
        state.zoomAround(focus, center, 3.5)
        val after = state.screenToWorld(focus, center)
        assertEquals(before.x, after.x, 1e-6)
        assertEquals(before.y, after.y, 1e-6)
    }

    @Test
    fun farPanRebasesOriginWithoutMovingContent() {
        val state = CanvasState()
        val center = Offset(500f, 500f)
        drawLine(state, WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0))
        repeat(1000) { state.panByScreenDelta(Offset(-1000f, 0f)) } // 1,000,000 world units to the right
        // Floating origin: the camera never drifts past the rebase threshold.
        assertTrue(abs(state.panWorld.x) <= 65536.0)
        // The stroke is still exactly one million units to the left of the camera.
        val strokeScreen = state.worldToScreen(state.strokes.single().points.first(), center)
        assertEquals(500.0 - 1_000_000.0, strokeScreen.x.toDouble(), 1.0)
    }
}
