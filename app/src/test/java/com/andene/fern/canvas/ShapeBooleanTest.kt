package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Roadmap item 96: union, intersection, subtraction and exclusion of closed shapes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeBooleanTest {
    private fun square(x: Double, y: Double, size: Double) = listOf(WorldPoint(x, y), WorldPoint(x + size, y), WorldPoint(x + size, y + size), WorldPoint(x, y + size))

    // Two 100 x 100 squares overlapping in a 50 x 100 band.
    private val a = square(0.0, 0.0, 100.0)
    private val b = square(50.0, 0.0, 100.0)

    private fun area(loops: List<List<WorldPoint>>) = loops.sumOf { abs(polygonArea(it)) }

    @Test
    fun unionIntersectAndSubtractHaveTheExpectedAreas() {
        assertEquals(15000.0, area(combineShapes(listOf(a, b), ShapeOp.UNION, bridgeHoles = true)), 1.0)
        assertEquals(5000.0, area(combineShapes(listOf(a, b), ShapeOp.INTERSECT, bridgeHoles = true)), 1.0)
        val cut = combineShapes(listOf(a, b), ShapeOp.SUBTRACT, bridgeHoles = true)
        assertEquals(5000.0, area(cut), 1.0)
        assertTrue(polygonContains(cut.single(), WorldPoint(25.0, 50.0)))
        assertEquals(2, combineShapes(listOf(a, b), ShapeOp.EXCLUDE, bridgeHoles = false).size)
    }

    @Test
    fun aHoleIsBridgedIntoOneFilledOutlineOrKeptApartForOutlines() {
        val frame = combineShapes(listOf(square(0.0, 0.0, 100.0), square(25.0, 25.0, 50.0)), ShapeOp.SUBTRACT, bridgeHoles = true)
        val outline = frame.single()
        assertTrue(polygonContains(outline, WorldPoint(10.0, 10.0)))
        assertTrue(!polygonContains(outline, WorldPoint(50.0, 50.0))) // the hole
        assertEquals(2, combineShapes(listOf(square(0.0, 0.0, 100.0), square(25.0, 25.0, 50.0)), ShapeOp.SUBTRACT, bridgeHoles = false).size)
    }

    @Test
    fun combiningTheSelectionReplacesItWithTheResultAndUndoes() {
        val state = CanvasState()
        fun filledSquare(x: Double, color: androidx.compose.ui.graphics.Color) = Stroke(color, 2.0, filled = true).apply {
            for (p in square(x, 0.0, 100.0) + WorldPoint(x, 0.0)) addPoint(p)
        }
        state.loadStrokes(listOf(filledSquare(0.0, androidx.compose.ui.graphics.Color.Red), filledSquare(50.0, androidx.compose.ui.graphics.Color.Blue)))
        state.selectAllOnActiveLayer()
        state.combineSelection(ShapeOp.SUBTRACT)
        val result = state.strokes.single()
        assertEquals(androidx.compose.ui.graphics.Color.Red, result.color) // the bottom shape's style
        assertTrue(result.filled)
        assertEquals(5000.0, abs(polygonArea(result.points)), 1.0)
        state.undo()
        assertEquals(2, state.strokes.size)
    }

    @Test
    fun disjointIntersectionIsEmpty() {
        assertTrue(combineShapes(listOf(a, square(500.0, 0.0, 10.0)), ShapeOp.INTERSECT, bridgeHoles = true).isEmpty())
    }
}
