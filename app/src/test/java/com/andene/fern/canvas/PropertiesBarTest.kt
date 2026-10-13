package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D9: the properties bar shows only what the active tool or the selection uses. */
@RunWith(RobolectricTestRunner::class)
class PropertiesBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun eachToolShowsItsOwnSettings() {
        val state = CanvasState()
        compose.setContent { Column { Toolbar(state) } }
        compose.onNodeWithContentDescription("Brush size").assertExists()
        compose.onNodeWithContentDescription("Pen type: Marker").assertExists()
        compose.onAllNodesWithContentDescription("Select all on this layer").assertCountEquals(0)

        state.activeTool = Tool.ERASER
        compose.onNodeWithContentDescription("Ink eraser (tap for object eraser)").assertExists()
        compose.onAllNodesWithContentDescription("Pen type: Marker").assertCountEquals(0)

        state.activeTool = Tool.SELECT
        compose.onNodeWithContentDescription("Select all on this layer").assertExists()
        compose.onAllNodesWithContentDescription("Brush size").assertCountEquals(0)

        state.activeTool = Tool.FILL
        compose.onNodeWithContentDescription("Screentone fill: off").assertExists()
    }
}
