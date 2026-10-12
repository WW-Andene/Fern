package com.andene.fern.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D11: workspaces set the toolbar's groups and the rail's pins, and the user's own persist. */
@RunWith(RobolectricTestRunner::class)
class WorkspacesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun applyingAWorkspaceSetsGroupsAndPins() {
        val animate = BUILT_IN_WORKSPACES.first { it.id == "animate" }
        var shown: Set<ToolbarGroup>? = null
        WorkspaceStore.apply(context, animate) { shown = it }
        assertEquals(animate.hiddenGroups, shown)
        assertEquals(animate.hiddenGroups, AppSettings.hiddenToolbarGroups(context))
        assertEquals(animate.pinned, RailSettings.pinned(context))
        assertEquals("animate", WorkspaceStore.activeId(context))
    }

    @Test
    fun customWorkspacesRoundTrip() {
        val mine = listOf(Workspace("x", setOf(ToolbarGroup.REPLAY), listOf(Tool.MEASURE, Tool.TEXT), name = "Plans"))
        WorkspaceStore.setCustom(context, mine)
        assertEquals(mine, WorkspaceStore.custom(context))
    }
}
