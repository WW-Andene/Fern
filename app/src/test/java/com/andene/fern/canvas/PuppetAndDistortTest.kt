package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 122: puppet warp and corner distort, alongside the existing mesh warp. */
class PuppetAndDistortTest {
    private fun assertNear(expected: WorldPoint, actual: WorldPoint) {
        assertEquals(expected.x, actual.x, 1e-6)
        assertEquals(expected.y, actual.y, 1e-6)
    }

    @Test
    fun puppetWithNoPinsOrOnePinMovesRigidly() {
        val p = WorldPoint(3.0, 4.0)
        assertEquals(p, puppetWarp(p, emptyList(), emptyList()))
        assertNear(WorldPoint(13.0, 4.0), puppetWarp(p, listOf(WorldPoint.Zero), listOf(WorldPoint(10.0, 0.0))))
    }

    @Test
    fun puppetPinsTurnWhatLiesBetweenThem() {
        // Two pins turned a quarter turn about the first: the midpoint turns with them, unstretched.
        val origins = listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))
        val pins = listOf(WorldPoint(0.0, 0.0), WorldPoint(0.0, 100.0))
        assertNear(WorldPoint(0.0, 50.0), puppetWarp(WorldPoint(50.0, 0.0), origins, pins))
        assertNear(WorldPoint(0.0, 100.0), puppetWarp(WorldPoint(100.0, 0.0), origins, pins))
    }

    @Test
    fun distortMapsCornersAndKeepsLinesStraight() {
        val min = WorldPoint(0.0, 0.0)
        val max = WorldPoint(100.0, 100.0)
        assertNear(WorldPoint(30.0, 70.0), perspectiveDistort(WorldPoint(30.0, 70.0), min, max, boxCorners(min, max)))
        val quad = listOf(WorldPoint(20.0, 0.0), WorldPoint(80.0, 0.0), WorldPoint(100.0, 100.0), WorldPoint(0.0, 100.0))
        for ((corner, target) in boxCorners(min, max).zip(quad)) assertNear(target, perspectiveDistort(corner, min, max, quad))
        // Three points on one line in the box are still on one line after the distort.
        val (a, b, c) = listOf(WorldPoint(10.0, 20.0), WorldPoint(50.0, 50.0), WorldPoint(90.0, 80.0)).map { perspectiveDistort(it, min, max, quad) }
        val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        assertEquals(0.0, cross, 1e-6)
        // A trapezoid is a real perspective: the box's centre doesn't land on the quad's centroid.
        val centre = perspectiveDistort(WorldPoint(50.0, 50.0), min, max, quad)
        assertEquals(37.5, centre.y, 1e-6)
    }

    @Test
    fun puppetPinsAreTappedInDraggedAndTappedOut() {
        val state = CanvasState()
        state.loadStrokes(listOf(Stroke(Color.Black, 2.0).apply { listOf(0.0, 50.0, 100.0).forEach { addPoint(WorldPoint(it, 0.0)) } }))
        state.selection.addAll(state.strokes)
        state.activeTool = Tool.SELECT
        state.toggleWarp(WarpKind.PUPPET)
        assertTrue(state.meshWarping)
        fun tap(at: WorldPoint) { state.beginSelectGesture(at); state.endSelectGesture() }
        tap(WorldPoint(0.0, 0.0))
        tap(WorldPoint(100.0, 0.0))
        assertEquals(2, state.meshNodes.size)
        state.beginSelectGesture(WorldPoint(100.0, 0.0))
        state.continueSelectGesture(WorldPoint(0.0, 100.0))
        state.endSelectGesture()
        val line = state.strokes.single()
        assertNear(WorldPoint(0.0, 50.0), line.points[1])
        assertNear(WorldPoint(0.0, 100.0), line.points[2])
        tap(WorldPoint(0.0, 100.0)) // pulls that pin out; the ink stays bent
        assertEquals(1, state.meshNodes.size)
        assertNear(WorldPoint(0.0, 100.0), state.strokes.single().points[2])
        // Switching to another warp kind replaces the puppet.
        state.toggleWarp(WarpKind.DISTORT)
        assertEquals(WarpKind.DISTORT, state.warpKind)
        assertEquals(4, state.meshNodes.size)
    }
}
