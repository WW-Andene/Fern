package com.andene.fern.canvas

import com.andene.fern.FernScale
import com.andene.fern.FernPanelHost
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import androidx.compose.foundation.layout.Column
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
        // The toolbar and its properties bar (design roadmap D9) are siblings; stacked here as the app places them apart.
        // The favourite slots live in the More panel since the toolbar strip was retired (roadmap item 339).
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Column { Toolbar(state) }
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithContentDescription("Empty favourite 1, tap to save the current pen").performScrollTo().performClick()
        assertEquals(PenType.PENCIL, state.toolSlots[0]?.penType)

        state.activePenType = PenType.MARKER
        state.activeColor = Color.Blue
        compose.onNodeWithContentDescription("Favourite 1: Pencil").performScrollTo().performClick()
        assertEquals(PenType.PENCIL, state.activePenType)
        assertEquals(Color.Red, state.activeColor)
    }
}
