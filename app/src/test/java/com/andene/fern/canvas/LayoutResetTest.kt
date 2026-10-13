package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

/** Design roadmap D85: one surface, or every one, back as Fern starts; the preview names what changes. */
class LayoutResetTest {
    private val arranged = DEFAULT_LAYOUT.copy(
        pinned = listOf(Tool.MEASURE),
        quickMenu = listOf(QuickMenuAction.UNDO),
        gestures = DEFAULT_LAYOUT.gestures + (CanvasGesture.LONG_PRESS to GestureAction.NONE),
    )

    @Test
    fun oneSurfaceGoesBackAndTheRestStays() {
        val reset = arranged.withDefaults(setOf(LayoutSurface.RAIL))
        assertEquals(DEFAULT_PINNED_TOOLS, reset.pinned)
        assertEquals(listOf(QuickMenuAction.UNDO), reset.quickMenu)
        assertEquals(setOf(LayoutSurface.RAIL), arranged.differencesFrom(reset))
    }

    @Test
    fun everythingGoesBackAndThePreviewListsOnlyWhatDiffered() {
        val reset = arranged.withDefaults(LayoutSurface.entries.toSet())
        assertEquals(DEFAULT_LAYOUT.copy(name = arranged.name), reset)
        assertEquals(setOf(LayoutSurface.RAIL, LayoutSurface.QUICK_MENU, LayoutSurface.GESTURES), arranged.differencesFrom(reset))
        assertEquals(emptySet<LayoutSurface>(), reset.differencesFrom(reset.withDefaults(LayoutSurface.entries.toSet())))
    }
}
