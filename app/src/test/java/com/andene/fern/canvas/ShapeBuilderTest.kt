package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.abs

/** Roadmap item 141: shape builder (divide, merge, delete), knife and contour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeBuilderTest {
    private fun square(x: Double, y: Double, size: Double) = listOf(WorldPoint(x, y), WorldPoint(x + size, y), WorldPoint(x + size, y + size), WorldPoint(x, y + size))

    private fun area(loops: List<List<WorldPoint>>) = loops.sumOf { abs(polygonArea(it)) }

    private fun filledSquare(x: Double, color: Color) = Stroke(color, 2.0, filled = true).apply {
        for (p in square(x, 0.0, 100.0) + WorldPoint(x, 0.0)) addPoint(p)
    }

    @Test
    fun twoOverlappingSquaresDivideIntoThreeRegions() {
        val regions = divideShapes(listOf(square(0.0, 0.0, 100.0), square(50.0, 0.0, 100.0)))
        assertEquals(3, regions.size)
        assertEquals(15000.0, area(regions), 1.0)
        regions.forEach { assertEquals(5000.0, abs(polygonArea(it)), 1.0) }
    }

    @Test
    fun aShapeCutInTwoGivesTwoSeparateRegions() {
        // A tall bar across a wide one splits the wide one's rest into a left and a right part.
        val regions = divideShapes(listOf(listOf(WorldPoint(0.0, 40.0), WorldPoint(300.0, 40.0), WorldPoint(300.0, 60.0), WorldPoint(0.0, 60.0)), square(100.0, 0.0, 100.0).map { WorldPoint(it.x, it.y) }))
        // Left of the bar, the overlap, right of the bar, and the bar above and below the overlap.
        assertEquals(5, regions.size)
    }

    @Test
    fun knifeCutsASquareInTwoAndMissesWhenItStopsInside() {
        val pieces = cutShape(square(0.0, 0.0, 100.0), listOf(WorldPoint(50.0, -10.0), WorldPoint(50.0, 110.0)), gap = 1.0)
        assertEquals(2, pieces.size)
        assertEquals(9900.0, area(pieces), 2.0)
        assertTrue(cutShape(square(0.0, 0.0, 100.0), listOf(WorldPoint(50.0, -10.0), WorldPoint(50.0, 50.0)), gap = 1.0).isEmpty())
    }

    @Test
    fun knifeSplitsALineAtEachCrossing() {
        val line = listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))
        val runs = splitPolyline(line, listOf(WorldPoint(30.0, -5.0), WorldPoint(30.0, 5.0), WorldPoint(70.0, 5.0), WorldPoint(70.0, -5.0)))
        assertEquals(3, runs.size)
        assertEquals(30.0, runs[0].last().x, 1e-9)
        assertEquals(70.0, runs[2].first().x, 1e-9)
        assertEquals(1, splitPolyline(line, listOf(WorldPoint(0.0, 10.0), WorldPoint(100.0, 10.0))).size)
    }

    @Test
    fun contourGrowsAndShrinksASquare() {
        val square = ContourSource(square(0.0, 0.0, 100.0), width = 0.0, closed = true)
        val bevel = contourOutlines(listOf(square), 10.0, ContourJoin.MITRE)
        assertEquals(120.0 * 120.0, area(bevel), 20.0)
        val inset = contourOutlines(listOf(square), -10.0, ContourJoin.MITRE)
        assertEquals(80.0 * 80.0, area(inset), 20.0)
        assertTrue(contourOutlines(listOf(square), -60.0, ContourJoin.MITRE).isEmpty())
        // A dot's round contour is a circle of the distance plus half its width.
        val dot = contourOutlines(listOf(ContourSource(listOf(WorldPoint(0.0, 0.0), WorldPoint(0.0, 0.0)), 4.0, false)), 8.0, ContourJoin.ROUND)
        assertEquals(PI * 10.0 * 10.0, area(dot), 10.0)
    }

    @Test
    fun interiorPointOfAConcaveShapeIsInside() {
        val ell = listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), WorldPoint(100.0, 10.0), WorldPoint(10.0, 10.0), WorldPoint(10.0, 100.0), WorldPoint(0.0, 100.0))
        assertTrue(polygonContains(ell, interiorPoint(ell)))
    }

    @Test
    fun shapeBuilderDividesThenMergesAndDeletesRegionsUndoably() {
        val state = CanvasState()
        state.loadStrokes(listOf(filledSquare(0.0, Color.Red), filledSquare(50.0, Color.Blue)))
        state.activeTool = Tool.SELECT
        state.selectAllOnActiveLayer()
        state.toggleShapeEdit(ShapeEditMode.BUILD_ADD)
        assertEquals(ShapeEditMode.BUILD_ADD, state.shapeEditMode)
        assertEquals(3, state.strokes.size)
        // The overlap takes the top (blue) shape's style.
        assertEquals(Color.Blue, state.strokes.single { polygonContains(it.points, WorldPoint(75.0, 50.0)) }.color)
        // Drag from the left region into the overlap: they merge.
        state.beginSelectGesture(WorldPoint(25.0, 50.0))
        state.continueSelectGesture(WorldPoint(75.0, 50.0))
        state.endSelectGesture()
        assertEquals(2, state.strokes.size)
        assertEquals(10000.0, abs(polygonArea(state.strokes.single { polygonContains(it.points, WorldPoint(25.0, 50.0)) }.points)), 2.0)
        // Delete the right region.
        state.toggleShapeEdit(ShapeEditMode.BUILD_REMOVE)
        state.beginSelectGesture(WorldPoint(125.0, 50.0))
        state.endSelectGesture()
        assertEquals(1, state.strokes.size)
        state.undo()
        state.undo()
        state.undo()
        assertEquals(2, state.strokes.size)
        state.activeTool = Tool.PEN
        assertNull(state.shapeEditMode)
    }

    @Test
    fun knifeModeCutsTheSelection() {
        val state = CanvasState()
        state.loadStrokes(listOf(filledSquare(0.0, Color.Red)))
        state.activeTool = Tool.SELECT
        state.selectAllOnActiveLayer()
        state.toggleShapeEdit(ShapeEditMode.KNIFE)
        state.beginSelectGesture(WorldPoint(50.0, -10.0))
        state.continueSelectGesture(WorldPoint(50.0, 110.0))
        state.endSelectGesture()
        assertEquals(2, state.strokes.size)
        assertTrue(state.strokes.all { it.color == Color.Red && it.filled })
        assertEquals(2, state.selection.size)
    }

    @Test
    fun contourAddsSelectedOutlinesAroundTheSelection() {
        val state = CanvasState()
        state.loadStrokes(listOf(filledSquare(0.0, Color.Red)))
        state.selectAllOnActiveLayer()
        state.contourSelection(10.0, ContourJoin.MITRE)
        assertEquals(2, state.strokes.size)
        val contour = state.strokes[1]
        assertTrue(!contour.filled)
        assertEquals(listOf(contour), state.selection.toList())
        assertEquals(120.0 * 120.0, abs(polygonArea(contour.points)), 20.0)
    }
}
