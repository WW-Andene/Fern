package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 172: every canvas handle reads out its value and takes a typed one. */
@RunWith(RobolectricTestRunner::class)
class HandleValuesTest {
    @Test
    fun aTypedPositionMovesTheHandleAsADragWould() {
        val state = CanvasState()
        state.setPerspective(PerspectiveMode.ONE_POINT)
        state.activeTool = Tool.PEN
        val handle = state.canvasHandles().single()
        val value = handle.typedValue(null)
        assertEquals(listOf(handle.position.x, handle.position.y), value.fields.map { it.read() })
        value.apply(listOf(300.0, -50.0))
        assertEquals(WorldPoint(300.0, -50.0), state.vanishingPoints.single())
        assertEquals(listOf(300.0, -50.0), value.fields.map { it.read() }) // read live
    }

    @Test
    fun theScaleHandleTakesASizeAndTheRotateHandleATurn() {
        val state = CanvasState()
        val box = Stroke(Color.Black, 2.0).apply {
            for (p in listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), WorldPoint(100.0, 50.0), WorldPoint(0.0, 50.0))) addPoint(p)
        }
        state.loadStrokes(listOf(box))
        state.selection.add(box)
        state.activeTool = Tool.SELECT
        val (scale, rotate) = state.canvasHandles()
        val size = scale.typedValue(null)
        assertEquals(100.0, size.fields[0].read(), 1e-9)
        size.apply(listOf(200.0, 100.0))
        val (min, max) = state.selectionBounds()!!
        assertEquals(200.0, max.x - min.x, 1e-6)
        assertEquals(100.0, max.y - min.y, 1e-6)
        // Turning by hand reads out the turn so far.
        val turn = rotate.typedValue(null).fields.single()
        val centre = WorldPoint((min.x + max.x) / 2, (min.y + max.y) / 2)
        state.beginRotateSelection(WorldPoint(centre.x + 10, centre.y))
        state.continueRotateSelection(WorldPoint(centre.x, centre.y + 10))
        assertEquals(90.0, turn.read(), 1e-6)
        state.endRotateSelection()
        assertEquals(0.0, turn.read(), 0.0)
    }
}
