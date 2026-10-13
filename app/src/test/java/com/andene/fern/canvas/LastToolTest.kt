package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D80: one action swaps to the previous tool and back; borrowing a tool doesn't count. */
@RunWith(RobolectricTestRunner::class)
class LastToolTest {
    @Test
    fun swapsBetweenTheLastTwoToolsWithTheirOwnSettings() {
        val state = CanvasState()
        state.activeWidthWorld = 4.0
        state.activeTool = Tool.FILL
        state.activeTool = Tool.SELECT
        state.swapToPreviousTool()
        assertEquals(Tool.FILL, state.activeTool)
        state.swapToPreviousTool()
        assertEquals(Tool.SELECT, state.activeTool)
        state.activeTool = Tool.PEN
        state.activeTool = Tool.ERASER
        state.swapToPreviousTool()
        assertEquals(Tool.PEN, state.activeTool)
        assertEquals(4.0, state.activeWidthWorld, 0.0) // the pen's own size came back with it (D81)
    }

    @Test
    fun borrowingAToolForAMomentLeavesTheLastToolAlone() {
        val state = CanvasState()
        state.activeTool = Tool.FILL
        state.activeTool = Tool.PEN
        // The stylus's eraser end, a held rail tool or key: in and out.
        val back = state.borrowTool(Tool.ERASER)
        state.returnTool(back)
        assertEquals(Tool.PEN, state.activeTool)
        state.swapToPreviousTool()
        assertEquals(Tool.FILL, state.activeTool)
    }
}
