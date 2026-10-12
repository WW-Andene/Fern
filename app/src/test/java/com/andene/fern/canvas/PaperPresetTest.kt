package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 73: light and dark paper presets, and a pen that stays visible on them. */
class PaperPresetTest {
    @Test
    fun darkPaperSwitchesAnInvisiblePenAndKeepsTheStyle() {
        val state = CanvasState()
        state.setBackground(state.backgroundColor, BackgroundStyle.DOT)
        state.activeColor = Color.Black
        state.applyPaperPreset(PaperPreset.DARK)
        assertEquals(PaperPreset.DARK.background, state.backgroundColor)
        assertEquals(BackgroundStyle.DOT, state.backgroundStyle)
        assertEquals(Color.White, state.activeColor)
        state.applyPaperPreset(PaperPreset.LIGHT)
        assertEquals(Color.Black, state.activeColor)
    }

    @Test
    fun aVisiblePenIsLeftAlone() {
        val state = CanvasState()
        state.activeColor = Color.Red
        state.applyPaperPreset(PaperPreset.DARK)
        assertEquals(Color.Red, state.activeColor)
    }

    @Test
    fun eachPresetsInkIsHighContrast() {
        for (paper in PaperPreset.entries) assertTrue(paper.name, contrastRatio(paper.ink, paper.background) >= 7.0)
    }
}
