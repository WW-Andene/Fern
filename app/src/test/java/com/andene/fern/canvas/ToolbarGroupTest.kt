package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 71: hidden toolbar groups disappear from the toolbar, core tools stay, and the choice persists. */
@RunWith(RobolectricTestRunner::class)
class ToolbarGroupTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hiddenGroupsAreLeftOutButCoreToolsRemain() {
        compose.setContent { Toolbar(CanvasState(), hiddenGroups = setOf(ToolbarGroup.LAYERS, ToolbarGroup.VIEW)) }
        compose.onAllNodesWithContentDescription("Layers").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Reset view").assertCountEquals(0)
        compose.onNodeWithContentDescription("Undo").assertExists()
        compose.onNodeWithContentDescription("Eraser").assertExists()
        compose.onNodeWithContentDescription("Fill").assertExists()
    }

    @Test
    fun hiddenGroupsPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(emptySet<ToolbarGroup>(), AppSettings.hiddenToolbarGroups(context))
        AppSettings.setHiddenToolbarGroups(context, setOf(ToolbarGroup.REPLAY, ToolbarGroup.GUIDES))
        assertEquals(setOf(ToolbarGroup.REPLAY, ToolbarGroup.GUIDES), AppSettings.hiddenToolbarGroups(context))
    }
}
