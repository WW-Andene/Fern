package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.math.hypot

/** Roadmap item 140: spray and bristle brush engines, and tags on brush presets. */
@RunWith(RobolectricTestRunner::class)
class BrushEnginesTest {
    @Test
    fun sprayScattersOverTheTipAndRepeats() {
        val particles = sprayParticles(WorldPoint(10.0, 10.0), 20.0, seed = 3)
        assertTrue(particles.size > 50)
        assertTrue(particles.all { hypot(it.x - 10.0, it.y - 10.0) <= 20.0 + 1e-9 })
        // Even by area: about a quarter of them within half the radius.
        val inner = particles.count { hypot(it.x - 10.0, it.y - 10.0) <= 10.0 }.toDouble() / particles.size
        assertTrue(inner in 0.12..0.4)
        assertEquals(particles, sprayParticles(WorldPoint(10.0, 10.0), 20.0, seed = 3))
    }

    @Test
    fun bristleHairsLieAcrossTheStrokeAndDryOut() {
        val hairs = bristleHairs(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), radius = 6.0, travelled = 0.0, seed = 1)
        assertEquals(12, hairs.size)
        assertTrue(hairs.all { abs(it.first.x - 10.0) < 1e-9 }) // across a horizontal stroke: one column
        assertTrue(hairs.maxOf { it.first.y } - hairs.minOf { it.first.y } > 9.0)
        val dry = bristleHairs(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), radius = 6.0, travelled = 2000.0, seed = 1)
        assertTrue(dry.sumOf { it.second.toDouble() } < hairs.sumOf { it.second.toDouble() })
    }

    @Test
    fun theEnginesPaintOnARasterLayer() {
        for (mode in listOf(RasterBrushMode.SPRAY, RasterBrushMode.BRISTLE)) {
            val state = CanvasState()
            state.addRasterLayer(8)
            state.activeColor = Color.Black
            state.activeWidthWorld = 24.0
            state.rasterBrush = RasterBrush(mode = mode)
            state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(60.0, 0.0)); state.endStroke()
            val surface = state.rasters.getValue(state.activeLayerId)
            val covered = (0..60).flatMap { x -> (-12..12).map { y -> surface.pixel(x, y)[3] } }
            assertTrue("$mode paints", covered.any { it > 0.3f })
            assertTrue("$mode leaves gaps", covered.any { it < 0.05f }) // grain or hair gaps, unlike a solid round brush
        }
    }

    @Test
    fun presetTagsAreSetKeptAndSaved() {
        val state = CanvasState()
        state.saveBrushPreset("Ink", StampBrush())
        state.setBrushPresetTags("Ink", listOf(" ink ", "line", "ink", ""))
        assertEquals(listOf("ink", "line"), state.brushPresets.single().tags)
        state.saveBrushPreset("Ink", StampBrush(spacing = 0.5f)) // replacing keeps the tags
        assertEquals(listOf("ink", "line"), state.brushPresets.single().tags)
        val context: Context = ApplicationProvider.getApplicationContext()
        AppSettings.setBrushPresets(context, state.brushPresets.toList())
        assertEquals(listOf("ink", "line"), AppSettings.brushPresets(context).single().tags)
    }
}
