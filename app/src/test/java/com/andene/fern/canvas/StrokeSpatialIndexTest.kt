package com.andene.fern.canvas

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StrokeSpatialIndexTest {
    private fun stroke(x: Double, y: Double, size: Double, width: Double = 1.0) =
        Stroke(Color.Black, width).apply { addPoint(WorldPoint(x, y)); addPoint(WorldPoint(x + size, y + size * 0.5)) }

    @Test
    fun matchesABruteForceScanAtEveryScale() {
        val random = Random(7)
        // Sizes from 1e-6 to 1e6 world units, scattered over a large area.
        val strokes = List(3000) {
            val size = Math.pow(10.0, random.nextDouble() * 12 - 6)
            stroke(random.nextGaussian() * 1e4, random.nextGaussian() * 1e4, size, size * 0.1)
        }
        val index = StrokeSpatialIndex(strokes)
        repeat(500) {
            val w = Math.pow(10.0, random.nextDouble() * 10 - 4)
            val left = random.nextGaussian() * 1e4
            val top = random.nextGaussian() * 1e4
            val expected = strokes.indices.filter { strokes[it].intersects(left, top, left + w, top + w) }
            val found = index.query(left, top, left + w, top + w).toList()
            assertTrue("missed ${expected - found.toSet()}", found.containsAll(expected))
            assertEquals(found.sorted(), found) // drawing order
        }
    }

    @Test
    fun aSmallViewReturnsFewCandidates() {
        val strokes = List(10_000) { i -> stroke((i % 100) * 64.0, (i / 100) * 64.0, 16.0) }
        val found = StrokeSpatialIndex(strokes).query(0.0, 0.0, 100.0, 100.0)
        assertTrue("got ${found.size}", found.size in 4..36)
    }

    @Test
    fun snapshotListIdentityIsStableUntilItChanges() {
        val list = mutableStateListOf(stroke(0.0, 0.0, 1.0))
        val first = list.toList()
        assertSame(first, list.toList())
        list.add(stroke(1.0, 1.0, 1.0))
        assertNotSame(first, list.toList())
    }

    @Test
    fun aMovedThenDeselectedStrokeIsFoundWhereItNowIs() {
        val state = CanvasState()
        state.loadStrokes(listOf(stroke(0.0, 0.0, 10.0), stroke(1000.0, 1000.0, 10.0)))
        state.strokesNear(-50.0, -50.0, 50.0, 50.0) // builds the index
        state.selection.add(state.strokes.first())
        state.beginSelectGesture(WorldPoint(5.0, 2.0))
        state.continueSelectGesture(WorldPoint(505.0, 502.0))
        state.endSelectGesture()
        state.beginSelectGesture(WorldPoint(-5000.0, -5000.0)) // tap away: deselect
        state.endSelectGesture()
        assertTrue(state.selection.isEmpty())
        assertTrue(state.strokesNear(490.0, 490.0, 520.0, 520.0).contains(state.strokes.first()))
        assertTrue(state.strokesNear(-50.0, -50.0, 50.0, 50.0).none { it === state.strokes.first() && it.intersects(-50.0, -50.0, 50.0, 50.0) })
    }

    @Test
    fun theStrokeBeingDrawnIsFoundAsItGrows() {
        val state = CanvasState()
        state.loadStrokes(listOf(stroke(0.0, 0.0, 10.0)))
        state.beginStroke(WorldPoint(0.0, 0.0))
        state.strokesNear(-10.0, -10.0, 10.0, 10.0)
        for (x in 1..300) state.extendStroke(WorldPoint(x * 10.0, 0.0))
        val drawing = state.strokeInProgress!!
        assertTrue(state.strokesNear(2900.0, -10.0, 3010.0, 10.0).contains(drawing))
        state.endStroke()
    }

    @Test
    fun afterARebaseStrokesAreFoundInTheNewOrigin() {
        val state = CanvasState()
        state.loadStrokes(listOf(stroke(0.0, 0.0, 10.0)))
        state.strokesNear(-50.0, -50.0, 50.0, 50.0)
        repeat(100) { state.panByScreenDelta(Offset(-1000f, 0f)) } // rebases
        val s = state.strokes.single()
        assertTrue(state.strokesNear(s.minX - 1, s.minY - 1, s.maxX + 1, s.maxY + 1).contains(s))
    }
}
