package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Roadmap item 138: the global selection mask, a selection painted with the brush. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionMaskTest {
    private fun painted(): CanvasState = CanvasState().apply {
        addRasterLayer(8)
        activeColor = Color.Red
        activeWidthWorld = 16.0
        beginStroke(WorldPoint(10.0, 20.0)); extendStroke(WorldPoint(50.0, 20.0)); endStroke()
    }

    @Test
    fun aPaintedSelectionLimitsWhatIsDeleted() {
        val state = painted()
        val surface = state.rasters.getValue(state.activeLayerId)
        state.togglePaintingSelection()
        assertTrue(state.paintingSelection)
        // A dab of selection on the right end of the line.
        state.beginStroke(WorldPoint(45.0, 20.0)); state.endStroke()
        assertEquals(1f, surface.pixel(15, 20)[0], 0.02f) // painting the selection doesn't touch the layer
        state.togglePaintingSelection()
        assertFalse(state.paintingSelection)
        assertTrue(state.hasRasterSelection)

        state.deleteRasterSelectionPixels()
        assertEquals(0f, surface.pixel(45, 20)[3], 1e-6f) // selected: cleared
        assertTrue(surface.pixel(15, 20)[3] > 0.9f) // not selected: kept
        state.undo()
        assertTrue(surface.pixel(45, 20)[3] > 0.9f)
    }

    @Test
    fun anOutlineBecomesTheMaskAndANewOutlineReplacesIt() {
        val state = painted()
        state.activeTool = Tool.SELECT
        state.beginSelectGesture(WorldPoint(0.0, 0.0)); state.continueSelectGesture(WorldPoint(30.0, 40.0)); state.endSelectGesture()
        assertTrue(state.rasterSelection != null)
        state.togglePaintingSelection()
        assertNull(state.rasterSelection)
        val mask = state.selectionMask!!
        assertTrue(mask.pixel(15, 20)[3] > 0.9f) // the outline's inside, now painted coverage
        assertEquals(0f, mask.pixel(45, 20)[3], 1e-6f)
        state.togglePaintingSelection()
        state.beginSelectGesture(WorldPoint(40.0, 0.0)); state.continueSelectGesture(WorldPoint(60.0, 40.0)); state.endSelectGesture()
        assertNull(state.selectionMask)
        assertTrue(state.rasterSelection != null)
        state.clearRasterSelection()
        assertFalse(state.hasRasterSelection)
    }
}
