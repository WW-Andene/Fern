package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap item 104: indexed palettes for pixel art. */
class IndexedPaletteTest {
    private val darkRed = Color(0xFF8B0000)
    private val navy = Color(0xFF000080)

    @Test
    fun pixelPenSnapsToTheNearestSavedColourWhenIndexed() {
        val state = CanvasState()
        state.addSavedColor(darkRed)
        state.addSavedColor(navy)
        state.activePenType = PenType.PIXEL
        state.indexedPalette = true
        state.activeColor = Color(0xFFCC1111) // reddish
        state.beginStroke(WorldPoint(0.0, 0.0))
        state.endStroke()
        assertEquals(darkRed, state.strokes.single().color)
    }

    @Test
    fun selectionReducesToThePaletteAndUndoes() {
        val state = CanvasState()
        state.addSavedColor(darkRed)
        state.addSavedColor(navy)
        val blueish = Stroke(Color(0xFF2020A0), 2.0).apply { addPoint(WorldPoint.Zero) }
        state.loadStrokes(listOf(blueish))
        state.selectAllOnActiveLayer()
        state.reduceSelectionToPalette()
        assertEquals(navy, state.strokes.single().color)
        state.undo()
        assertEquals(Color(0xFF2020A0), state.strokes.single().color)
    }
}
