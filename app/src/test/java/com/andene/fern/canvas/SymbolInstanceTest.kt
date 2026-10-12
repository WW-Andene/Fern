package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 100: placed symbols stay linked, and follow when the symbol is redefined. */
class SymbolInstanceTest {
    private fun line(x0: Double, x1: Double, color: Color = Color.Black) =
        Stroke(color, 2.0).apply { addPoint(WorldPoint(x0, 0.0)); addPoint(WorldPoint(x1, 0.0)) }

    @Test
    fun instancesFollowARedefinitionAtTheirOwnPlaceAndSize() {
        val symbol = SymbolMeta("s", "Arrow", revision = 1)
        val master = listOf(line(0.0, 10.0))
        val state = CanvasState()
        state.placeSymbol(symbol, master)
        val first = state.symbolInstances.single()
        assertEquals(10.0, first.masterWidth, 1e-9)
        // A second instance, moved away and drawn at twice the size.
        state.panTo(WorldPoint(200.0, 0.0))
        state.placeSymbol(symbol, master)
        val second = state.symbolInstances.last()
        for (stroke in state.strokes.filter { it.groupId == second.groupId }) stroke.setPoints(stroke.points.map { WorldPoint(200 + (it.x - 200) * 2, it.y) })

        // Redefined: twice as long and red.
        state.syncSymbol(SymbolMeta("s", "Arrow", revision = 2), listOf(line(0.0, 20.0, Color.Red)))
        fun widthOf(group: String) = state.strokes.filter { it.groupId == group }.let { s -> s.maxOf { it.maxX } - s.minOf { it.minX } }
        assertTrue(state.strokes.all { it.color == Color.Red })
        // The master doubled; each instance doubles at its own scale.
        assertEquals(20.0, widthOf(first.groupId), 1e-9)
        assertEquals(40.0, widthOf(second.groupId), 1e-9)
        assertEquals(200.0, state.strokes.filter { it.groupId == second.groupId }.let { s -> (s.minOf { it.minX } + s.maxOf { it.maxX }) / 2 }, 1e-9)
        assertTrue(state.symbolInstances.all { it.revision == 2L })
        state.undo()
        assertTrue(state.strokes.all { it.color == Color.Black })
    }

    @Test
    fun redefiningFromAScaledInstanceKeepsTheMasterSize() {
        val state = CanvasState()
        state.placeSymbol(SymbolMeta("s", "Dash"), listOf(line(0.0, 10.0)))
        val instance = state.symbolInstances.single()
        // Drawn at three times the size, then selected.
        for (stroke in state.strokes) stroke.setPoints(stroke.points.map { WorldPoint(it.x * 3, it.y) })
        state.selection.addAll(state.strokes)
        val (picked, newMaster) = state.symbolRedefinitionFromSelection()!!
        assertEquals(instance.groupId, picked.groupId)
        assertEquals(10.0, strokesWidth(newMaster), 1e-9) // back at the master's own size

        state.selection.addAll(state.strokes)
        state.detachSymbolInstance()
        assertTrue(state.symbolInstances.isEmpty())
        assertNull(state.selectedSymbolInstance)
    }
}
