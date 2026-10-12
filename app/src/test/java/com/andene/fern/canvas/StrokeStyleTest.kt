package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 121: the style picker copies one object's style and pastes it onto others. */
class StrokeStyleTest {
    private fun line(x: Double, color: Color, width: Double, pen: PenType = PenType.MARKER, dashed: Boolean = false) =
        Stroke(color, width, pen, dashed = dashed).apply { addPoint(WorldPoint(x, 0.0)); addPoint(WorldPoint(x, 10.0)) }

    @Test
    fun pastedStyleRedrawsTheSelectionAndUndoes() {
        val state = CanvasState()
        state.loadStrokes(listOf(line(0.0, Color.Red, 8.0, PenType.PENCIL, dashed = true), line(100.0, Color.Blue, 2.0)))
        state.selection.add(state.strokes[0])
        state.copySelectionStyle()
        state.selection.clear()
        state.selection.add(state.strokes[1])
        state.pasteStyleToSelection()

        val restyled = state.strokes[1]
        assertEquals(Color.Red, restyled.color)
        assertEquals(8.0, restyled.widthWorld, 1e-9)
        assertEquals(PenType.PENCIL, restyled.penType)
        assertTrue(restyled.dashed)
        assertEquals(WorldPoint(100.0, 10.0), restyled.points.last()) // geometry untouched
        assertEquals(listOf(restyled), state.selection.toList())

        state.undo()
        assertEquals(Color.Blue, state.strokes[1].color)
        assertEquals(2.0, state.strokes[1].widthWorld, 1e-9)
    }

    @Test
    fun anOutlinesStyleOnlyRecoloursAFill() {
        val fill = Stroke(Color.Green, 1.0, filled = true, screentone = true).apply {
            addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(10.0, 0.0)); addPoint(WorldPoint(0.0, 10.0))
        }
        val restyled = fill.withStyle(StrokeStyle.of(line(0.0, Color.Red, 8.0, PenType.PENCIL, dashed = true)))
        assertEquals(Color.Red, restyled.color)
        assertTrue(restyled.filled)
        assertTrue(restyled.screentone)
        assertEquals(1.0, restyled.widthWorld, 1e-9)
        assertEquals(PenType.MARKER, restyled.penType)
    }
}
