package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 99: screen rulers and the guides pulled out of them. */
class RulerGuidesTest {
    @Test
    fun ticksStepByOneTwoOrFiveAndRescaleWithZoom() {
        assertEquals(100.0, rulerStep(1.0), 1e-9) // 64 px minimum: 100 units at 1 px per unit
        assertEquals(50.0, rulerStep(2.0), 1e-9)
        assertEquals(0.2, rulerStep(400.0), 1e-9)
        val ticks = rulerTicks(0.0, 100.0, 2.0)
        assertEquals(listOf(0.0, 50.0, 100.0), ticks.filter { it.major }.map { it.position })
        assertEquals(11, ticks.size) // a 50 step in fifths
        assertEquals(listOf(0.0, 0.2, 0.4), rulerTicks(0.0, 0.4, 400.0).filter { it.major }.map { Math.round(it.position * 10) / 10.0 })
        assertEquals("50", rulerLabel(50.0, 50.0))
        assertEquals("1.5", rulerLabel(1.5, 0.5))
        assertEquals("0", rulerLabel(-0.0001, 1.0))
    }

    @Test
    fun aMovedSelectionSnapsToAGuide() {
        val state = CanvasState()
        state.viewportSize = Size(400f, 400f)
        state.loadStrokes(listOf(Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(10.0, 0.0)) }))
        val id = state.addGuide(vertical = true, position = 100.0)
        state.selection.addAll(state.strokes)
        state.activeTool = Tool.SELECT
        state.beginSelectGesture(WorldPoint(5.0, 0.0))
        state.continueSelectGesture(WorldPoint(93.0, 0.0)) // right edge would land at 98
        state.endSelectGesture()
        assertEquals(100.0, state.strokes.single().maxX, 1e-9)
        state.moveGuide(id, 120.0)
        assertEquals(120.0, state.guides.single().position, 0.0)
        state.removeGuide(id)
        assertTrue(state.guides.isEmpty())
    }
}
