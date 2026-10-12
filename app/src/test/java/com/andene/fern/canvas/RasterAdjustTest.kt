package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Roadmap item 92: levels, curves, hue/saturation, liquify and raster warp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterAdjustTest {
    @Test
    fun levelsCurvesAndHueMapColours() {
        assertEquals(1f, Levels(inWhite = 0.5f).map(0.5f), 1e-4f)
        assertEquals(0.25f, Levels(outWhite = 0.5f).map(0.5f), 1e-4f)
        val curve = Curves(listOf(0f to 0f, 0.5f to 0.8f, 1f to 1f))
        assertEquals(0.8f, curve.map(0.5f), 0.01f)
        val samples = (0..100).map { curve.map(it / 100f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - 1e-6f }) // monotone
        val rgb = floatArrayOf(1f, 0f, 0f)
        HueSaturation(hueDegrees = 120f).apply(rgb)
        assertEquals(1f, rgb[1], 0.01f) // red turned green
    }

    private fun painted(): CanvasState = CanvasState().apply {
        addRasterLayer(8)
        activeColor = Color.Red
        activeWidthWorld = 16.0
        beginStroke(WorldPoint(20.0, 20.0)); extendStroke(WorldPoint(40.0, 20.0)); endStroke()
    }

    @Test
    fun anAdjustmentPreviewsThenAppliesOrCancels() {
        val state = painted()
        val surface = state.rasters.getValue(state.activeLayerId)
        assertTrue(state.beginAdjusting())
        state.previewAdjustment(HueSaturation(hueDegrees = 120f))
        assertEquals(1f, surface.pixel(30, 20)[1], 0.02f)
        state.cancelAdjustment()
        assertEquals(1f, surface.pixel(30, 20)[0], 0.02f)
        state.beginAdjusting()
        state.previewAdjustment(HueSaturation(hueDegrees = 120f))
        state.commitAdjustment()
        assertEquals(1f, surface.pixel(30, 20)[1], 0.02f)
        state.undo()
        assertEquals(1f, surface.pixel(30, 20)[0], 0.02f)
    }

    @Test
    fun liquifyAndWarpMovePaint() {
        val state = painted()
        val surface = state.rasters.getValue(state.activeLayerId)
        state.rasterBrush = RasterBrush(RasterBrushMode.LIQUIFY, strength = 1f)
        state.activeWidthWorld = 40.0
        state.beginStroke(WorldPoint(30.0, 20.0)); state.extendStroke(WorldPoint(30.0, 32.0)); state.endStroke()
        assertTrue(surface.pixel(30, 30)[3] > 0.2f) // paint pushed down
        // Warp: drag the whole lattice's top-left node far away; paint near it follows.
        state.toggleMeshWarp()
        assertTrue(state.meshWarping)
        val before = surface.tiles.values.sumOf { t -> (3 until t.data.size step 4).count { (t.data[it].toInt() and 0xFFFF) > 0 } }
        state.activeTool = Tool.SELECT
        val node = state.meshNodes[5]
        state.beginSelectGesture(node)
        state.continueSelectGesture(WorldPoint(node.x + 60, node.y + 60))
        state.endSelectGesture()
        val after = surface.tiles.values.sumOf { t -> (3 until t.data.size step 4).count { (t.data[it].toInt() and 0xFFFF) > 0 } }
        assertTrue(after > 0 && after != before)
        state.undo()
        assertEquals(before, surface.tiles.values.sumOf { t -> (3 until t.data.size step 4).count { (t.data[it].toInt() and 0xFFFF) > 0 } })
    }
}
