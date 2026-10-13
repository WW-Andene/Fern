package com.andene.fern

import org.junit.Assert.assertEquals
import org.junit.Test

/** Design roadmap D52: the layout follows the window's width, phone first. */
class FernLayoutTest {
    @Test
    fun phoneUprightGetsTheBottomRailAndSheets() {
        assertEquals(FernLayout(railAtBottom = true, dockedPanels = 1), FernLayout.of(360))
        assertEquals(FernLayout(railAtBottom = true, dockedPanels = 1), FernLayout.of(599))
    }

    @Test
    fun widerWindowsDockBesideASideRail() {
        // A phone on its side and a tablet held upright: one docked panel.
        assertEquals(FernLayout(railAtBottom = false, dockedPanels = 1), FernLayout.of(800))
        assertEquals(FernLayout(railAtBottom = false, dockedPanels = 1), FernLayout.of(600))
    }

    @Test
    fun wideWindowsDockSeveralPanelsKeepingTheCanvas() {
        // 64 rail + 384 canvas + 2 x 384 panels = 1216.
        assertEquals(1, FernLayout.of(1215).dockedPanels)
        assertEquals(2, FernLayout.of(1216).dockedPanels)
        assertEquals(3, FernLayout.of(1600).dockedPanels)
        assertEquals(3, FernLayout.of(3000).dockedPanels)
    }
}
