package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeEditingTest {
    @Test
    fun shortStrokesShowEveryPointLongOnesASpreadIncludingEnds() {
        assertEquals(listOf(0, 1, 2, 3, 4), editNodeIndices(5))
        val long = editNodeIndices(500)
        assertEquals(MAX_EDIT_NODES, long.size)
        assertEquals(0, long.first())
        assertEquals(499, long.last())
    }

    @Test
    fun draggingAShapeCornerMovesOnlyThatCorner() {
        val square = listOf(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), WorldPoint(10.0, 10.0), WorldPoint(0.0, 10.0))
        val moved = dragNode(square, editNodeIndices(4), 2, WorldPoint(5.0, 5.0))
        assertEquals(listOf(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), WorldPoint(15.0, 15.0), WorldPoint(0.0, 10.0)), moved)
    }

    @Test
    fun draggingAFreehandNodeBendsItsNeighbourhoodSmoothly() {
        val line = List(101) { WorldPoint(it.toDouble(), 0.0) }
        val nodes = listOf(0, 50, 100)
        val moved = dragNode(line, nodes, 1, WorldPoint(0.0, 10.0))
        assertEquals(10.0, moved[50].y, 0.0)
        assertEquals(0.0, moved[0].y, 0.0)
        assertEquals(0.0, moved[100].y, 0.0)
        assertTrue(moved[25].y in 4.0..6.0) // halfway: eased to half
        assertTrue((1..49).all { moved[it].y <= moved[it + 1].y }) // rises monotonically to the node
    }

    @Test
    fun dragInTheCanvasReshapesAndUndoes() {
        val state = CanvasState()
        val stroke = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)) }
        state.loadStrokes(listOf(stroke))
        state.selection.add(stroke)
        state.toggleNodeEditing()
        assertTrue(state.nodeEditing)
        state.beginSelectGesture(WorldPoint(101.0, 1.0)) // on the end node
        state.continueSelectGesture(WorldPoint(121.0, 31.0))
        state.endSelectGesture()
        assertEquals(WorldPoint(120.0, 30.0), stroke.points.last())
        state.undo()
        assertEquals(WorldPoint(100.0, 0.0), state.strokes.single().points.last())
    }

    @Test
    fun tappingAwayEndsNodeEditing() {
        val state = CanvasState()
        val stroke = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)) }
        state.loadStrokes(listOf(stroke))
        state.selection.add(stroke)
        state.toggleNodeEditing()
        state.beginSelectGesture(WorldPoint(500.0, 500.0))
        state.endSelectGesture()
        assertFalse(state.nodeEditing)
    }

    @Test
    fun needsExactlyOneSelectedStroke() {
        val state = CanvasState()
        state.toggleNodeEditing()
        assertFalse(state.nodeEditing)
    }
}
