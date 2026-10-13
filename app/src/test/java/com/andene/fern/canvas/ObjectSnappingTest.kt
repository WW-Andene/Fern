package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObjectSnappingTest {
    private fun polyline(vararg points: WorldPoint) = Stroke(Color.Black, 2.0).apply { points.forEach { addPoint(it) } }

    @Test
    fun snapsToShapeCornersAndStrokeEnds() {
        val square = polyline(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), WorldPoint(100.0, 100.0), WorldPoint(0.0, 100.0), WorldPoint(0.0, 0.0))
        assertEquals(WorldPoint(100.0, 100.0), nearestSnapAnchor(listOf(square), WorldPoint(97.0, 104.0), 10.0))
        assertNull(nearestSnapAnchor(listOf(square), WorldPoint(50.0, 50.0), 10.0))
    }

    @Test
    fun longFreehandStrokesOfferOnlyTheirEnds() {
        val freehand = polyline(*Array(40) { WorldPoint(it * 5.0, 0.0) })
        assertNull(nearestSnapAnchor(listOf(freehand), WorldPoint(100.0, 1.0), 4.0)) // a middle point
        assertEquals(WorldPoint(195.0, 0.0), nearestSnapAnchor(listOf(freehand), WorldPoint(196.0, 2.0), 4.0))
    }

    @Test
    fun nearestWinsAndExcludedStrokesAreIgnored() {
        val a = polyline(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0))
        val b = polyline(WorldPoint(3.0, 0.0), WorldPoint(50.0, 0.0))
        assertEquals(WorldPoint(3.0, 0.0), nearestSnapAnchor(listOf(a, b), WorldPoint(2.5, 0.0), 5.0))
        assertEquals(WorldPoint(0.0, 0.0), nearestSnapAnchor(listOf(a, b), WorldPoint(2.5, 0.0), 5.0) { it === a })
    }

    @Test
    fun shapeToolSnapsBothEndsToExistingCorners() {
        val state = CanvasState()
        state.loadStrokes(listOf(polyline(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), WorldPoint(100.0, 100.0))))
        state.activeShapeKind = ShapeKind.LINE
        state.shapeTool.begin(WorldPoint(3.0, -4.0))
        state.shapeTool.move(WorldPoint(96.0, 103.0))
        assertEquals(WorldPoint(100.0, 100.0), state.snapIndicator)
        state.shapeTool.end()
        assertNull(state.snapIndicator)
        val line = state.strokes.last()
        assertEquals(listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 100.0)), line.points.toList())
    }

    @Test
    fun snappingCanBeTurnedOff() {
        val state = CanvasState()
        state.loadStrokes(listOf(polyline(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))))
        state.snapToObjects = false
        state.activeShapeKind = ShapeKind.LINE
        state.shapeTool.begin(WorldPoint(3.0, -4.0))
        state.shapeTool.move(WorldPoint(60.0, 70.0))
        state.shapeTool.end()
        assertEquals(WorldPoint(3.0, -4.0), state.strokes.last().points.first())
    }
}
