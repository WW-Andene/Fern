package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshWarpTest {
    private val min = WorldPoint(0.0, 0.0)
    private val max = WorldPoint(90.0, 30.0)

    @Test
    fun anUntouchedLatticeChangesNothing() {
        val grid = meshGrid(min, max)
        for (p in listOf(WorldPoint(0.0, 0.0), WorldPoint(17.0, 9.0), WorldPoint(90.0, 30.0))) {
            val q = meshWarp(p, min, max, grid)
            assertEquals(p.x, q.x, 1e-9)
            assertEquals(p.y, q.y, 1e-9)
        }
    }

    @Test
    fun movingTheWholeLatticeMovesTheInk() {
        val shifted = meshGrid(min, max).map { WorldPoint(it.x + 5, it.y - 2) }
        val q = meshWarp(WorldPoint(40.0, 10.0), min, max, shifted)
        assertEquals(45.0, q.x, 1e-9)
        assertEquals(8.0, q.y, 1e-9)
    }

    @Test
    fun draggingALatticePointBendsTheSelectionAndUndoes() {
        val state = CanvasState()
        val line = Stroke(Color.Black, 2.0).apply { for (x in 0..90 step 10) addPoint(WorldPoint(x.toDouble(), 0.0)) }
        state.loadStrokes(listOf(line))
        state.selection.add(line)
        state.toggleMeshWarp()
        assertTrue(state.meshWarping)
        val node = state.meshNodes[5] // second row, second column: inside the box
        state.beginSelectGesture(node)
        state.continueSelectGesture(WorldPoint(node.x, node.y - 40))
        state.endSelectGesture()
        // Bent upward, most where the dragged point pulls, least at the far end.
        val lift = line.points.map { -it.y }
        assertTrue(lift.max() > 5.0) // 40 x B1(1/3) x B1(1/2) ≈ 6.7 at most
        assertTrue(lift.last() < lift.max() / 4)
        state.undo()
        assertTrue(line.points.all { kotlin.math.abs(it.y) < 1e-9 })
        assertFalse(state.meshWarping) // undo clears the selection, and with it the lattice
    }
}
