package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The copy-on-write undo history: snapshots share stroke data yet never change. */
class UndoHistoryTest {
    private fun line(x: Double) = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(x, 0.0), 0.5f); addPoint(WorldPoint(x + 10, 0.0), 0.75f) }

    @Test
    fun aCapturedStateNeverChanges() {
        val stroke = line(0.0)
        val state = stroke.captureState()
        stroke.addPoint(WorldPoint(99.0, 99.0))
        stroke.shiftBy(WorldPoint(5.0, 5.0))
        stroke.setPoints(stroke.points.map { WorldPoint(it.x * 2, it.y) })
        stroke.restoreState(state)
        assertEquals(listOf(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0)), stroke.points.toList())
        assertEquals(listOf(0.5f, 0.75f), stroke.pressures.toList())
        assertEquals(10.0, stroke.maxX, 0.0)
    }

    @Test
    fun copiesAreIndependentBothWays() {
        val original = line(0.0)
        val copy = original.deepCopy()
        copy.addPoint(WorldPoint(50.0, 0.0), 0.1f)
        copy.shiftBy(WorldPoint(1.0, 0.0))
        assertEquals(2, original.points.size)
        assertEquals(0.0, original.points.first().x, 0.0)
        original.shiftBy(WorldPoint(-100.0, 0.0))
        assertEquals(-1.0, copy.points.first().x, 0.0)
        assertEquals(listOf(0.5f, 0.75f, 0.1f), copy.pressures.toList())
    }

    @Test
    fun moveUndoRedo() {
        val state = CanvasState()
        state.loadStrokes(listOf(line(0.0), line(100.0)))
        state.selection.add(state.strokes.first())
        state.beginSelectGesture(WorldPoint(5.0, 0.0))
        state.continueSelectGesture(WorldPoint(25.0, 30.0))
        state.endSelectGesture()
        assertEquals(WorldPoint(20.0, 30.0), state.strokes.first().points.first())
        state.undo()
        assertEquals(WorldPoint(0.0, 0.0), state.strokes.first().points.first())
        assertEquals(WorldPoint(100.0, 0.0), state.strokes.last().points.first())
        state.redo()
        assertEquals(WorldPoint(20.0, 30.0), state.strokes.first().points.first())
    }

    @Test
    fun undoAfterARebaseKeepsStrokesWhereTheyWereOnScreen() {
        val state = CanvasState()
        val center = Offset(500f, 500f)
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(10.0, 5.0)); state.endStroke()
        state.beginStroke(WorldPoint(0.0, 50.0)); state.extendStroke(WorldPoint(10.0, 55.0)); state.endStroke()
        repeat(100) { state.panByScreenDelta(Offset(-1000f, 0f)) } // far enough to rebase
        val firstOnScreen = state.worldToScreen(state.strokes.first().points.first(), center)
        state.undo() // removes the second stroke, restoring a snapshot taken before the rebase
        assertEquals(1, state.strokes.size)
        val afterUndo = state.worldToScreen(state.strokes.single().points.first(), center)
        assertEquals(firstOnScreen.x, afterUndo.x, 0.01f)
        assertEquals(firstOnScreen.y, afterUndo.y, 0.01f)
    }

    @Test
    fun persistedHistoryMaterializesIndependentStrokes() {
        val state = CanvasState()
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(10.0, 5.0)); state.endStroke()
        state.beginStroke(WorldPoint(0.0, 50.0)); state.extendStroke(WorldPoint(10.0, 55.0)); state.endStroke()
        val (undo, _) = state.historySnapshot()
        assertEquals(listOf(0, 1), undo.map { it.size })
        val saved = undo[1].single()
        assertTrue(saved !== state.strokes.first())
        state.strokes.first().shiftBy(WorldPoint(7.0, 7.0))
        assertEquals(0.0, saved.points.first().x, 0.0) // not affected by later edits
    }

    @Test
    fun historySavedAfterARebaseIsInTheCurrentOrigin() {
        val state = CanvasState()
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(10.0, 5.0)); state.endStroke()
        state.beginStroke(WorldPoint(0.0, 50.0)); state.extendStroke(WorldPoint(10.0, 55.0)); state.endStroke()
        repeat(100) { state.panByScreenDelta(Offset(-1000f, 0f)) }
        val live = state.strokes.first().points.first()
        val saved = state.historySnapshot().first[1].single().points.first()
        assertEquals(live.x, saved.x, 1e-9)
        assertEquals(live.y, saved.y, 1e-9)
    }
}
