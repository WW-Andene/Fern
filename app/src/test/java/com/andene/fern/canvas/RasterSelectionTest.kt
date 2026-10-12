package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Roadmap item 91: raster selections, layer masks, clipping masks and alpha lock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterSelectionTest {
    private fun rasterState() = CanvasState().apply {
        addRasterLayer(8)
        activeColor = Color.Black
        activeWidthWorld = 8.0
    }

    private fun line(state: CanvasState, from: WorldPoint, to: WorldPoint) {
        state.beginStroke(from)
        state.extendStroke(to)
        state.endStroke()
    }

    private fun selectRect(state: CanvasState, a: WorldPoint, b: WorldPoint) {
        state.activeTool = Tool.SELECT
        state.beginSelectGesture(a)
        state.continueSelectGesture(b)
        state.endSelectGesture()
        state.activeTool = Tool.PEN
    }

    @Test
    fun paintingStaysInsideTheSelection() {
        val state = rasterState()
        val surface = state.rasters.getValue(state.activeLayerId)
        selectRect(state, WorldPoint(0.0, 0.0), WorldPoint(50.0, 50.0))
        line(state, WorldPoint(10.0, 25.0), WorldPoint(90.0, 25.0))
        assertEquals(1f, surface.pixel(30, 25)[3], 0.01f)
        assertEquals(0f, surface.pixel(70, 25)[3], 0f)
    }

    @Test
    fun selectedPixelsClearFillAndMoveWithUndo() {
        val state = rasterState()
        val surface = state.rasters.getValue(state.activeLayerId)
        line(state, WorldPoint(10.0, 25.0), WorldPoint(40.0, 25.0))
        selectRect(state, WorldPoint(0.0, 0.0), WorldPoint(50.0, 50.0))
        // Drag the pixels 100 right.
        state.activeTool = Tool.SELECT
        state.beginSelectGesture(WorldPoint(25.0, 25.0))
        state.continueSelectGesture(WorldPoint(75.0, 25.0))
        state.continueSelectGesture(WorldPoint(125.0, 25.0))
        state.endSelectGesture()
        assertEquals(0f, surface.pixel(25, 25)[3], 0f)
        assertEquals(1f, surface.pixel(125, 25)[3], 0.01f)
        state.undo()
        assertEquals(1f, surface.pixel(25, 25)[3], 0.01f)
        assertEquals(0f, surface.pixel(125, 25)[3], 0f)
        // Fill, then clear, the (restored) selection.
        selectRect(state, WorldPoint(0.0, 0.0), WorldPoint(50.0, 50.0))
        state.fillRasterSelection()
        assertEquals(1f, surface.pixel(5, 45)[3], 0.01f)
        state.deleteRasterSelectionPixels()
        assertEquals(0f, surface.pixel(25, 25)[3], 0f)
    }

    @Test
    fun alphaLockRecoloursWithoutSpreading() {
        val state = rasterState()
        val surface = state.rasters.getValue(state.activeLayerId)
        line(state, WorldPoint(10.0, 25.0), WorldPoint(40.0, 25.0))
        state.updateLayer(state.activeLayerId) { it.copy(alphaLocked = true) }
        state.activeColor = Color.Red
        state.activeWidthWorld = 40.0
        line(state, WorldPoint(25.0, 25.0), WorldPoint(26.0, 25.0))
        assertEquals(1f, surface.pixel(25, 25)[0], 0.02f) // recoloured red
        assertEquals(0f, surface.pixel(25, 40)[3], 0f) // no paint added outside
    }

    private fun render(state: CanvasState): android.graphics.Bitmap {
        val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)
        val image = ImageBitmap(100, 100)
        val center = Offset(50f, 50f)
        state.jumpTo(WorldPoint(50.0, 50.0), 1.0)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(100f, 100f)) {
            drawRect(Color.White)
            drawScene(state, center, state.screenToWorld(Offset.Zero, center), state.screenToWorld(Offset(100f, 100f), center), textMeasurer, { null })
        }
        return image.asAndroidBitmap()
    }

    @Test
    fun aMaskHidesWhatItCoversAndClippedLayersFollowTheLayerBelow() {
        val state = rasterState()
        val base = state.activeLayerId
        state.activeWidthWorld = 40.0
        line(state, WorldPoint(20.0, 50.0), WorldPoint(80.0, 50.0)) // a black bar, y 30..70
        // A red layer clipped to the bar: red only where the bar is.
        state.addRasterLayer(8)
        state.updateLayer(state.activeLayerId) { it.copy(clipped = true) }
        state.activeColor = Color.Red
        state.activeWidthWorld = 100.0
        line(state, WorldPoint(50.0, 0.0), WorldPoint(50.0, 100.0))
        var bitmap = render(state)
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(50, 50) or 0xFF000000.toInt())
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(50, 10)) // outside the bar: nothing
        // Mask the base: hide its left end; the clipped red goes with it.
        state.activeLayerId = base
        state.addLayerMask(base)
        state.activeWidthWorld = 30.0
        line(state, WorldPoint(25.0, 50.0), WorldPoint(26.0, 50.0))
        bitmap = render(state)
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(25, 50))
        assertTrue(bitmap.getPixel(70, 50) != android.graphics.Color.WHITE)
    }
}
