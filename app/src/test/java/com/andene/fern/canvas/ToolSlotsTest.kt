package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToolSlotsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun saveAndRecallRestoresTheWholePen() {
        var persisted: List<ToolPreset?> = emptyList()
        val state = CanvasState(onToolSlotsChanged = { persisted = it })
        state.activePenType = PenType.STAMP
        state.activeColor = Color.Red
        state.activeWidthWorld = 12.0
        state.activeOpacity = 0.5
        state.activeDashed = true
        state.setBrush(StampBrush(StampTip.STAR))
        state.saveToolSlot(2)
        assertEquals(PenType.STAMP, persisted[2]?.penType)

        state.activePenType = PenType.MARKER
        state.activeColor = Color.Blue
        state.activeWidthWorld = 2.0
        state.activeTool = Tool.ERASER
        state.setBrush(StampBrush())
        state.applyToolSlot(2)
        assertEquals(PenType.STAMP, state.activePenType)
        assertEquals(Color.Red, state.activeColor)
        assertEquals(12.0, state.activeWidthWorld, 0.0)
        assertEquals(0.5, state.activeOpacity, 0.0)
        assertEquals(true, state.activeDashed)
        assertEquals(StampTip.STAR, state.activeBrush.tip)
        assertEquals(Tool.PEN, state.activeTool)

        state.clearToolSlot(2)
        assertNull(state.toolSlots[2])
    }

    @Test
    fun slotsPersistThroughSettings() {
        val preset = ToolPreset(PenType.CALLIGRAPHY, Color(0xFF336699), 6.0, 0.75, StrokeBlendMode.MULTIPLY, false, StampBrush(spacing = 1f))
        AppSettings.setToolSlots(context, listOf(null, preset, null, null))
        val loaded = AppSettings.toolSlots(context)
        assertEquals(4, loaded.size)
        assertNull(loaded[0])
        assertEquals(preset, loaded[1])
    }
}
