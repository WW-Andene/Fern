package com.andene.fern.canvas

import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D8: the tool rail's fixed tools, selection and pinned tools. */
@RunWith(RobolectricTestRunner::class)
class ToolRailTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun railToolsSelectAndShowWhichIsActive() {
        val state = CanvasState()
        compose.setContent { ToolRail(state, vertical = false, modifier = Modifier.width(768.dp)) }
        compose.onNodeWithContentDescription("Fill").performClick()
        assertEquals(Tool.FILL, state.activeTool)
        compose.onNodeWithContentDescription("Fill").assertIsSelected()
        // Every other tool is one level away, in "More tools".
        compose.onNodeWithContentDescription("More tools").performClick()
        compose.onNodeWithText("Bone").performClick()
        assertEquals(Tool.BONE, state.activeTool)
    }

    @Test
    fun moreToolsPinsAndPinnedToolsPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = CanvasState()
        compose.setContent { ToolRail(state, vertical = false, modifier = Modifier.width(768.dp)) }
        compose.onNodeWithContentDescription("More tools").performClick()
        compose.onNodeWithContentDescription("Pin Measure to the rail").performClick()
        assertEquals(DEFAULT_PINNED_TOOLS + Tool.MEASURE, RailSettings.pinned(context))
    }
}
