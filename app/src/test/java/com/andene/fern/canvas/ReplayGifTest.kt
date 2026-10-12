package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplayGifTest {
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)

    @Test
    fun replayGifShowsTheDrawingAppearing() {
        val state = CanvasState()
        state.loadStrokes(List(10) { i -> Stroke(Color.Black, 4.0).apply { addPoint(WorldPoint(i * 20.0, 0.0)); addPoint(WorldPoint(i * 20.0, 100.0)) } })
        val gif = exportReplayGif(state, textMeasurer, Density(1f)) { null }!!
        assertEquals("GIF89a", String(gif, 0, 6))
        // One image descriptor (0x2C) per frame follows each graphic control extension (21 F9 04).
        var frames = 0
        for (i in 0 until gif.size - 3) if (gif[i] == 0x21.toByte() && gif[i + 1] == 0xF9.toByte() && gif[i + 2] == 4.toByte()) frames++
        assertEquals(48 + 16, frames)
        java.io.File("/tmp/claude-0/-home-user-Fern/ee016339-5940-5ac4-9efb-a6c2bb7fd87d/scratchpad/replay.gif").writeBytes(gif)
        assertTrue(state.replayCount == null) // the live document's replay state is untouched
    }

    @Test
    fun emptyDocumentHasNoReplay() {
        assertNull(exportReplayGif(CanvasState(), textMeasurer, Density(1f)) { null })
    }
}
