package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap item 99: smart guides when moving, numeric transforms, shape dimensions. */
class SmartGuidesTest {
    private fun box(x: Double, y: Double, size: Double) = Stroke(Color.Black, 2.0).apply {
        for (p in generateShapePoints(ShapeKind.RECTANGLE, WorldPoint(x, y), WorldPoint(x + size, y + size))) addPoint(p)
    }

    @Test
    fun aMovedSelectionSnapsItsEdgeToANeighboursAndShowsTheGuide() {
        val state = CanvasState().apply { viewportSize = Size(800f, 800f) }
        // A wider neighbour (100..180), so only its left edge is the nearest line to snap to.
        val fixed = box(100.0, 0.0, 80.0)
        val moving = box(-200.0, 200.0, 50.0)
        state.loadStrokes(listOf(fixed, moving))
        state.selection.add(moving)
        state.beginSelectGesture(WorldPoint(-175.0, 225.0))
        // Drag right by 297: the left edge would land at 97, 3 units from the other box's left edge at 100.
        state.continueSelectGesture(WorldPoint(122.0, 225.0))
        assertEquals(100.0, moving.minX, 1e-9)
        assertEquals(100.0, state.smartGuides.first!!, 1e-9)
        state.endSelectGesture()
        assertEquals(null to null, state.smartGuides)
    }

    @Test
    fun numericTransformSetsBoundsAndUndoes() {
        val state = CanvasState()
        val shape = box(0.0, 0.0, 10.0)
        state.loadStrokes(listOf(shape))
        state.selection.add(shape)
        state.transformSelection(left = 50.0, top = 60.0, width = 40.0, height = 20.0, rotationDegrees = 0.0)
        val (min, max) = state.selectionBounds()!!
        assertEquals(WorldPoint(50.0, 60.0), min)
        assertEquals(90.0, max.x, 1e-9)
        assertEquals(80.0, max.y, 1e-9)
        state.undo()
        assertEquals(0.0, state.strokes.single().minX, 1e-9)
    }

    @Test
    fun dimensionsReadLengthOrWidthByHeight() {
        assertEquals("50 · -53°", shapeDimensions(ShapeKind.LINE, WorldPoint(0.0, 0.0), WorldPoint(30.0, 40.0)))
        assertEquals("30 × 40", shapeDimensions(ShapeKind.RECTANGLE, WorldPoint(0.0, 0.0), WorldPoint(30.0, -40.0)))
    }
}
