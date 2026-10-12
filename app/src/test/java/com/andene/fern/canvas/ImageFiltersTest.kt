package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Roadmap item 138: filters for raster layers and filter layers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageFiltersTest {
    private fun dot(size: Int): IntArray = IntArray(size * size).also { it[(size / 2) * size + size / 2] = 0xFFFFFFFF.toInt() }

    @Test
    fun blurSpreadsAPixelAndSharpenRestoresContrast() {
        val pixels = dot(21)
        GaussianBlur(3.0).filter(pixels, 21, 21, 3)
        val center = pixels[10 * 21 + 10] ushr 24
        val near = pixels[10 * 21 + 12] ushr 24
        assertTrue(center in 1..254 && near in 1 until center)
        assertEquals(0, pixels[0] ushr 24) // the blur doesn't reach the corner

        val edge = IntArray(16) { if (it % 4 < 2) 0xFF404040.toInt() else 0xFFC0C0C0.toInt() }
        Sharpen(1.0, 1f).filter(edge, 4, 4, 1)
        assertTrue((edge[1] and 0xFF) < 0x40 && (edge[2] and 0xFF) > 0xC0) // each side pushed apart
    }

    @Test
    fun pixelateAndColourFiltersAndTheirJson() {
        val pixels = IntArray(16) { i -> if (i == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        Pixelate(2.0).filter(pixels, 4, 4, 2)
        assertEquals(pixels[0], pixels[5]) // one 2x2 cell, one colour
        assertEquals(0xFF000000.toInt(), pixels[15])

        val rgb = floatArrayOf(0.2f, 0.6f, 0.9f)
        Invert.apply(rgb)
        assertEquals(0.8f, rgb[0], 1e-6f)
        Posterize(2).apply(rgb)
        assertTrue(rgb.all { it == 0f || it == 1f })
        val grey = floatArrayOf(0.7f, 0.7f, 0.7f).also { Threshold(0.5f).apply(it) }
        assertEquals(1f, grey[0])

        for (a in listOf(GaussianBlur(5.0), Sharpen(2.0, 1.5f), Pixelate(8.0), Invert, Posterize(6), Threshold(0.3f))) {
            assertEquals(a, adjustmentFromJson(adjustmentToJson(a)))
        }
    }

    @Test
    fun aRasterLayerIsBlurredPastItsPaintAndUndoes() {
        val state = CanvasState().apply {
            addRasterLayer(8)
            activeColor = Color.Black
            activeWidthWorld = 4.0
            beginStroke(WorldPoint(0.0, 0.0)); extendStroke(WorldPoint(40.0, 0.0)); endStroke()
        }
        val surface = state.rasters.getValue(state.activeLayerId)
        assertEquals(0f, surface.pixel(20, 6)[3], 1e-6f)
        assertTrue(state.beginAdjusting())
        state.previewAdjustment(GaussianBlur(4.0))
        state.commitAdjustment()
        assertTrue(surface.pixel(20, 6)[3] > 0f) // spread below the line
        state.undo()
        assertEquals(0f, surface.pixel(20, 6)[3], 1e-6f)
    }
}
