package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Roadmap item 142: isometric paper, planes and placement; layout grids on frames. */
@RunWith(RobolectricTestRunner::class)
class IsometricAndLayoutGridTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun planeMappingRoundTrips() {
        val origin = WorldPoint(10.0, 20.0)
        for (plane in IsometricPlane.entries) {
            val p = WorldPoint(37.0, -12.0)
            val back = plane.toFlat(plane.toScreenPlane(p, origin), origin)
            assertEquals(p.x, back.x, 1e-9)
            assertEquals(p.y, back.y, 1e-9)
        }
    }

    @Test
    fun aSquareOnTheTopPlaneIsARhombusWithThirtyDegreeEdges() {
        // A flat 100 x 100 square, as its flat-coordinate end is placed on the plane.
        val end = IsometricPlane.TOP.toScreenPlane(WorldPoint(100.0, 100.0), WorldPoint.Zero)
        val points = shapeOnPlane(ShapeKind.RECTANGLE, WorldPoint.Zero, end, IsometricPlane.TOP)
        val distinct = points.distinct()
        assertEquals(4, distinct.size)
        for (i in distinct.indices) {
            val a = distinct[i]
            val b = distinct[(i + 1) % distinct.size]
            assertEquals(100.0, hypot(b.x - a.x, b.y - a.y), 1e-6) // true length along the axes
            val degrees = Math.toDegrees(atan2(abs(b.y - a.y), abs(b.x - a.x)))
            assertEquals(30.0, degrees, 1e-6)
        }
    }

    @Test
    fun isometricSnappingLandsOnTheLattice() {
        val spacing = 40.0
        val snapped = snappedToIsometric(WorldPoint(36.0, 22.0), spacing)
        assertEquals(spacing * sqrt(3.0) / 2, snapped.x, 1e-9)
        assertEquals(20.0, snapped.y, 1e-9)
        // A lattice point stays put.
        val on = WorldPoint(2 * spacing * sqrt(3.0) / 2, spacing * 3)
        assertEquals(on.x, snappedToIsometric(on, spacing).x, 1e-9)
        assertEquals(on.y, snappedToIsometric(on, spacing).y, 1e-9)
        // The rebase period maps the lattice onto itself.
        val shifted = WorldPoint(on.x + isometricPeriodX(spacing), on.y)
        assertEquals(shifted.x, snappedToIsometric(shifted, spacing).x, 1e-9)
        assertEquals(shifted.y, snappedToIsometric(shifted, spacing).y, 1e-9)
    }

    @Test
    fun placingASelectionOnAPlaneMapsItAroundItsCentreUndoably() {
        val state = CanvasState()
        val square = Stroke(Color.Black, 2.0).apply {
            for (p in listOf(WorldPoint(-50.0, -50.0), WorldPoint(50.0, -50.0), WorldPoint(50.0, 50.0), WorldPoint(-50.0, 50.0))) addPoint(p)
        }
        state.loadStrokes(listOf(square))
        state.selectAllOnActiveLayer()
        state.placeSelectionOnPlane(IsometricPlane.LEFT)
        val placed = state.strokes.single().points
        assertEquals(IsometricPlane.LEFT.toScreenPlane(WorldPoint(50.0, -50.0), WorldPoint.Zero), placed[1])
        state.undo()
        assertEquals(WorldPoint(50.0, -50.0), state.strokes.single().points[1])
    }

    @Test
    fun layoutGridEdgesFillTheFrameInsideItsMargins() {
        val frame = Frame("f", "F", WorldPoint(0.0, 0.0), WorldPoint(1000.0, 500.0))
        val grid = LayoutGrid(columns = 4, rows = 0, gutter = 20.0, margin = 40.0)
        val edges = grid.columnEdges(frame)
        assertEquals(8, edges.size)
        assertEquals(40.0, edges.first(), 1e-9)
        assertEquals(960.0, edges.last(), 1e-9)
        assertEquals(20.0, edges[2] - edges[1], 1e-9) // gutter
        assertTrue(grid.rowEdges(frame).isEmpty())
        assertTrue(LayoutGrid(4, 0, 400.0, 400.0).columnEdges(frame).isEmpty()) // no room
    }

    @Test
    fun layoutGridIsSavedWithTheFrames() {
        val grid = LayoutGrid(12, 3, 16.0, 32.0)
        CanvasStorage.saveFrames(context, "doc", listOf(Frame("f", "F", WorldPoint(0.0, 0.0), WorldPoint(100.0, 100.0), grid), Frame("g", "G", WorldPoint(0.0, 0.0), WorldPoint(1.0, 1.0))))
        val loaded = CanvasStorage.loadFrames(context, "doc")
        assertEquals(grid, loaded[0].layoutGrid)
        assertEquals(null, loaded[1].layoutGrid)
    }

    @Test
    fun layoutGridEdgesAttractShapeCorners() {
        val state = CanvasState()
        state.loadFrames(listOf(Frame("f", "F", WorldPoint(0.0, 0.0), WorldPoint(1000.0, 500.0), LayoutGrid(4, 0, 20.0, 40.0))))
        state.activeTool = Tool.SHAPE
        state.activeShapeKind = ShapeKind.LINE
        state.shapeTool.begin(WorldPoint(100.0, 100.0))
        state.shapeTool.move(WorldPoint(263.0, 300.0)) // 8 from the first column's right edge at 40 + (960 - 40 - 3 x 20) / 4 = 255
        state.shapeTool.end()
        assertEquals(255.0, state.strokes.single().points.last().x, 1e-9)
    }
}
