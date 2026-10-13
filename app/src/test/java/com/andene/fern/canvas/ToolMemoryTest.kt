package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D81: each drawing tool keeps its own settings; the eraser has its own size. */
@RunWith(RobolectricTestRunner::class)
class ToolMemoryTest {
    @Test
    fun theEraserKeepsItsOwnSizeAndThePenItsOwn() {
        val state = CanvasState()
        state.activeWidthWorld = 4.0
        state.activeTool = Tool.ERASER
        assertEquals(48.0, state.activeWidthWorld, 0.0) // its default, not the pen's 4
        state.activeWidthWorld = 20.0
        state.activeTool = Tool.PEN
        assertEquals(4.0, state.activeWidthWorld, 0.0)
        state.toggleEraser()
        assertEquals(20.0, state.activeWidthWorld, 0.0)
    }

    @Test
    fun eachToolKeepsItsColourButAPickedColourGoesToTheNextOneUsed() {
        val state = CanvasState()
        state.activeColor = Color.Red
        state.activeTool = Tool.FILL
        state.activeColor = Color.Blue
        state.activeTool = Tool.PEN
        assertEquals(Color.Red, state.activeColor)
        // The eyedropper doesn't own settings: what it picks stays for the pen.
        state.activeTool = Tool.EYEDROPPER
        state.activeColor = Color.Green
        state.activeTool = Tool.PEN
        assertEquals(Color.Green, state.activeColor)
        state.activeTool = Tool.FILL
        assertEquals(Color.Blue, state.activeColor)
    }
}
