package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** UI test: the toolbar's favourite slots save the current pen and recall it. */
@RunWith(RobolectricTestRunner::class)
class ToolbarToolSlotsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tapSavesThenRecalls() {
        val state = CanvasState()
        state.activePenType = PenType.PENCIL
        state.activeColor = Color.Red
        compose.setContent { Toolbar(state) }
        compose.onNodeWithContentDescription("Empty favourite 1, tap to save the current pen").performClick()
        assertEquals(PenType.PENCIL, state.toolSlots[0]?.penType)

        state.activePenType = PenType.MARKER
        state.activeColor = Color.Blue
        compose.onNodeWithContentDescription("Favourite 1: Pencil").performClick()
        assertEquals(PenType.PENCIL, state.activePenType)
        assertEquals(Color.Red, state.activeColor)
    }
}
