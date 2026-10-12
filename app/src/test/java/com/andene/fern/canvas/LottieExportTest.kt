package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 87: the animation exports as a frame-by-frame Lottie file. */
@RunWith(RobolectricTestRunner::class)
class LottieExportTest {
    private fun line(frame: Int, y: Double, color: Color) = Stroke(color, 4.0, animFrame = frame).apply {
        addPoint(WorldPoint(0.0, y)); addPoint(WorldPoint(100.0, y))
    }

    @Test
    fun eachFrameIsALayerShownForThatFrameOverThePaper() {
        val state = CanvasState()
        state.loadStrokes(listOf(line(0, 0.0, Color.Red), line(1, 50.0, Color.Blue), line(1, 60.0, Color.Blue)))
        state.animFps = 8
        val json = JSONObject(exportLottie(state)!!)
        assertEquals(8, json.getInt("fr"))
        assertEquals(2, json.getInt("op"))
        val layers = json.getJSONArray("layers")
        assertEquals(3, layers.length()) // two frames, then the paper at the bottom
        for (f in 0..1) {
            val layer = layers.getJSONObject(f)
            assertEquals(f, layer.getInt("ip"))
            assertEquals(f + 1, layer.getInt("op"))
            assertEquals(f + 1, layer.getJSONArray("shapes").length())
        }
        assertEquals("Paper", layers.getJSONObject(2).getString("nm"))
        val stroke = layers.getJSONObject(0).getJSONArray("shapes").getJSONObject(0).getJSONArray("it").getJSONObject(1)
        assertEquals("st", stroke.getString("ty"))
        assertEquals(1.0, stroke.getJSONObject("c").getJSONArray("k").getDouble(0), 1e-9) // red
    }

    @Test
    fun anEmptyDocumentHasNoExport() {
        assertNull(exportLottie(CanvasState()))
    }
}
