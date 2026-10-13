package com.andene.fern.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D83: each workspace comes back as it was left - rail, quick menu, open panels. */
@RunWith(RobolectricTestRunner::class)
class WorkspaceMemoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun putTheSharedLayoutBack() {
        // The rail, quick menu and panels are app-wide state that outlives one test.
        RailSettings.setPinned(context, DEFAULT_PINNED_TOOLS)
        AppSettings.setQuickMenuActions(context, DEFAULT_QUICK_MENU)
        DockedPanels.show(emptySet())
    }

    @Test
    fun aWorkspaceComesBackAsItWasLeftAndResetForgetsIt() {
        val draw = BUILT_IN_WORKSPACES.first { it.id == "draw" }
        val paint = BUILT_IN_WORKSPACES.first { it.id == "paint" }
        var hidden = emptySet<ToolbarGroup>()
        WorkspaceStore.switchTo(context, draw, hidden) { hidden = it }
        // Changed while in Draw: another pinned tool, a shorter ring, the layers panel open.
        RailSettings.setPinned(context, listOf(Tool.MEASURE))
        AppSettings.setQuickMenuActions(context, listOf(QuickMenuAction.UNDO, QuickMenuAction.PEN))
        DockedPanels.layers = true
        WorkspaceStore.switchTo(context, paint, hidden) { hidden = it }
        assertEquals(paint.pinned, RailSettings.pinned(context))
        WorkspaceStore.switchTo(context, draw, hidden) { hidden = it }
        assertEquals(listOf(Tool.MEASURE), RailSettings.pinned(context))
        assertEquals(listOf(QuickMenuAction.UNDO, QuickMenuAction.PEN), AppSettings.quickMenuActions(context)) // in ring order
        assertEquals(setOf(WorkspacePanel.LAYERS), DockedPanels.open())
        // Reset: Draw as defined again.
        WorkspaceStore.forget(context, draw.id)
        WorkspaceStore.switchTo(context, draw, hidden) { hidden = it }
        assertEquals(draw.pinned, RailSettings.pinned(context))
    }
}
