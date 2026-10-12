package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class EraserToggleTest {
    @Test
    fun stylusButtonSwapsToTheEraserAndBack() {
        val state = CanvasState()
        state.activeTool = Tool.SHAPE
        state.toggleEraser()
        assertEquals(Tool.ERASER, state.activeTool)
        state.toggleEraser()
        assertEquals(Tool.SHAPE, state.activeTool)
    }
}
