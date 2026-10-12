package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 94: the fill tool closes gaps, fills on its own layer from reference line art, and undoes. */
class GapFillToolTest {
    private fun line(x0: Double, y0: Double, x1: Double, y1: Double) = Stroke(Color.Black, 2.0).apply {
        addPoint(WorldPoint(x0, y0)); addPoint(WorldPoint(x1, y1))
    }

    private fun lineArt(): CanvasState = CanvasState().apply {
        viewportSize = Size(400f, 400f)
        // A square of four lines centred on the view, its corners left 2 units open.
        loadStrokes(listOf(line(-48.0, -50.0, 48.0, -50.0), line(50.0, -48.0, 50.0, 48.0), line(48.0, 50.0, -48.0, 50.0), line(-50.0, 48.0, -50.0, -48.0)))
    }

    @Test
    fun colourOnItsOwnLayerFillsTheLeakyShapeOfTheReferenceLayer() {
        val state = lineArt()
        val inkLayer = state.layers.single().id
        state.updateLayer(inkLayer) { it.copy(reference = true) }
        state.addLayer()
        val colourLayer = state.activeLayerId
        state.activeColor = Color.Red
        state.fillAt(WorldPoint(0.0, 0.0))
        val fill = state.strokes.single { it.filled }
        assertEquals(colourLayer, fill.layerId)
        assertTrue(polygonContains(fill.points, WorldPoint(45.0, 45.0)))
        assertTrue(!polygonContains(fill.points, WorldPoint(60.0, 0.0)))
        state.undo()
        assertTrue(state.strokes.none { it.filled })
    }

    @Test
    fun tappingOpenSpaceFillsNothingAndAddsNoUndoStep() {
        val state = lineArt()
        state.fillAt(WorldPoint(150.0, 150.0))
        assertTrue(state.strokes.none { it.filled })
        state.undo() // nothing to undo: the line art stays
        assertEquals(4, state.strokes.size)
    }
}
