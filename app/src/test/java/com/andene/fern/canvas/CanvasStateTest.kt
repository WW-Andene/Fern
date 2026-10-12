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

    private val square = listOf(WorldPoint(-10.0, -10.0), WorldPoint(10.0, -10.0), WorldPoint(10.0, 10.0), WorldPoint(-10.0, 10.0))

    private fun lassoErase(state: CanvasState, loop: List<WorldPoint>) {
        state.beginErase(loop.first())
        loop.drop(1).forEach { state.continueErase(it) }
        state.endErase()
    }

    @Test
    fun lassoEraseCutsInkInsideTheLoop() {
        val state = CanvasState()
        // A horizontal line through the loop, with points every unit from -30 to 30.
        state.beginStroke(WorldPoint(-30.0, 0.0))
        for (x in -29..30) state.extendStroke(WorldPoint(x.toDouble(), 0.0))
        state.endStroke()
        state.lassoErase = true
        lassoErase(state, square)
        assertEquals(2, state.strokes.size)
        // Points exactly on the loop's edge may go either way (ray casting); nothing strictly inside survives.
        assertTrue(state.strokes.all { stroke -> stroke.points.all { abs(it.x) >= 10.0 } })
        state.undo()
        assertEquals(1, state.strokes.size)
    }

    @Test
    fun lassoObjectEraseRemovesWholeStrokesTouched() {
        val state = CanvasState()
        drawLine(state, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0)) // has a point inside the loop
        drawLine(state, WorldPoint(50.0, 50.0), WorldPoint(100.0, 50.0)) // entirely outside
        state.lassoErase = true
        state.eraseWholeObjects = true
        lassoErase(state, square)
        assertEquals(1, state.strokes.size)
        assertEquals(50.0, state.strokes.single().points.first().y, 0.0)
    }

    @Test
    fun lassoTapLeavesNoUndoStep() {
        val state = CanvasState()
        drawLine(state, WorldPoint(50.0, 50.0), WorldPoint(100.0, 50.0))
        val depth = state.historyDepth
        state.lassoErase = true
        lassoErase(state, listOf(WorldPoint(0.0, 0.0)))
        assertEquals(depth, state.historyDepth)
        assertEquals(1, state.strokes.size)
    }
}
