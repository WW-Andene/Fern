package com.andene.fern.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Roadmap item 74: on a wide window the toolbar wraps, so its last tool is on screen without scrolling. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1024dp-h768dp")
class ToolbarWrapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wrappedToolbarShowsEveryTool() {
        compose.setContent { Box(Modifier.width(1024.dp)) { Toolbar(CanvasState(), wrap = true) } }
        compose.onNodeWithContentDescription("Ruler guide").assertIsDisplayed()
    }

    @Test
    fun scrollingToolbarLeavesTheLastToolOffScreen() {
        compose.setContent { Box(Modifier.width(1024.dp)) { Toolbar(CanvasState(), wrap = false) } }
        compose.onNodeWithContentDescription("Ruler guide").assertIsNotDisplayed()
    }
}
