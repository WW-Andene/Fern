package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PerspectiveGuideTest {
    @Test
    fun directionsPerMode() {
        val vp = listOf(WorldPoint(100.0, 0.0), WorldPoint(-100.0, 0.0))
        assertEquals(0, perspectiveDirections(PerspectiveMode.OFF, vp, WorldPoint.Zero).size)
        // One-point: toward the point, vertical, horizontal.
        assertEquals(3, perspectiveDirections(PerspectiveMode.ONE_POINT, vp, WorldPoint(0.0, 50.0)).size)
        // Two-point: toward each point, vertical.
        assertEquals(3, perspectiveDirections(PerspectiveMode.TWO_POINT, vp, WorldPoint(0.0, 50.0)).size)
    }

    @Test
    fun closestDirectionPicksTheBestLineEitherWay() {
        val candidates = listOf(WorldPoint(1.0, 0.0), WorldPoint(0.0, 1.0))
        assertEquals(WorldPoint(0.0, 1.0), closestDirection(candidates, WorldPoint.Zero, WorldPoint(1.0, -10.0)))
        assertEquals(WorldPoint(1.0, 0.0), closestDirection(candidates, WorldPoint.Zero, WorldPoint(-10.0, 2.0)))
    }

    private fun stateWith(mode: PerspectiveMode): CanvasState = CanvasState().apply {
        viewportSize = Size(1000f, 800f)
        setPerspective(mode)
    }

    @Test
    fun twoPointPlacesPointsOnAHorizonAroundTheView() {
        val state = stateWith(PerspectiveMode.TWO_POINT)
        assertEquals(listOf(WorldPoint(-400.0, 0.0), WorldPoint(400.0, 0.0)), state.vanishingPoints.toList())
        state.setPerspective(PerspectiveMode.OFF)
        assertTrue(state.vanishingPoints.isEmpty())
    }

    @Test
    fun strokeIsStraightenedTowardTheVanishingPoint() {
        val state = stateWith(PerspectiveMode.ONE_POINT) // vanishing point at (0, 0)
        val start = WorldPoint(200.0, 200.0)
        state.beginStroke(start)
        // Wobbly movement roughly toward the vanishing point (direction (-1, -1)).
        listOf(WorldPoint(190.0, 192.0), WorldPoint(178.0, 181.0), WorldPoint(160.0, 163.0), WorldPoint(140.0, 138.0)).forEach { state.extendStroke(it) }
        state.endStroke()
        val stroke = state.strokes.single()
        // Every point lies on the line from the start through the vanishing point (y = x).
        assertTrue(stroke.points.all { abs(it.x - it.y) < 1e-9 })
    }

    @Test
    fun nearVerticalStrokeSnapsVertical() {
        val state = stateWith(PerspectiveMode.TWO_POINT)
        state.beginStroke(WorldPoint(50.0, 100.0))
        listOf(WorldPoint(51.0, 120.0), WorldPoint(49.0, 140.0), WorldPoint(52.0, 160.0)).forEach { state.extendStroke(it) }
        state.endStroke()
        assertTrue(state.strokes.single().points.all { it.x == 50.0 })
    }

    @Test
    fun touchingAHandleMovesTheVanishingPointInsteadOfDrawing() {
        val state = stateWith(PerspectiveMode.ONE_POINT)
        state.activeTool = Tool.PEN
        state.dragHandle(WorldPoint(5.0, 5.0), WorldPoint(300.0, -50.0)) // within reach of the handle at (0, 0)
        assertTrue(state.strokes.isEmpty())
        assertEquals(WorldPoint(300.0, -50.0), state.vanishingPoints.single())
    }

    @Test
    fun shortStrokeIsLeftAlone() {
        val state = stateWith(PerspectiveMode.ONE_POINT)
        state.beginStroke(WorldPoint(200.0, 200.0))
        state.extendStroke(WorldPoint(205.0, 203.0)) // under the decision distance
        state.endStroke()
        assertEquals(WorldPoint(205.0, 203.0), state.strokes.single().points.last())
        assertTrue(hypot(5.0, 3.0) < 16.0)
    }
}
