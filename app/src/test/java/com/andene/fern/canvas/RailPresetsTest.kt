package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D84: a tool saved with its settings under a name, chosen from the rail. */
@RunWith(RobolectricTestRunner::class)
class RailPresetsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun clear() = RailPresets.set(context, emptyList()) // app-wide state that outlives one test

    @Test
    fun aPresetComesBackWithItsToolAndSettings() {
        val state = CanvasState()
        state.activeTool = Tool.ERASER
        state.activeWidthWorld = 12.0
        val soft = RailPreset("p", "Soft eraser", Tool.ERASER, state.currentToolSettings())
        RailPresets.set(context, listOf(soft))
        state.activeTool = Tool.PEN
        state.activeColor = Color.Red
        state.applyRailPreset(RailPresets.state(context).value.single())
        assertEquals(Tool.ERASER, state.activeTool)
        assertEquals(12.0, state.activeWidthWorld, 0.0)
    }

    @Test
    fun presetSettingsSurviveBeingSavedLineStyleIncluded() {
        val settings = ToolPreset(PenType.PENCIL, Color.Blue, 2.0, 0.5, StrokeBlendMode.MULTIPLY, true, StampBrush(), LineStyle(LineCap.SQUARE, LineJoin.BEVEL))
        assertEquals(settings, AppSettings.toolPresetFromJson(AppSettings.toolPresetToJson(settings)))
    }

    @Test
    fun aResizeSettlesOnAPresetsSize() {
        assertEquals(10.0, snapToPresetSize(10.4, listOf(10.0)), 0.0)
        assertEquals(10.4, snapToPresetSize(10.4), 0.0)
    }
}
