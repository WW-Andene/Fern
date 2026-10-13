package com.andene.fern

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text/background pair clears WCAG AA (4.5:1); in the high-contrast schemes, AAA (7:1). */
class FernThemeTest {
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    @Test
    fun highContrastPairsExceedSevenToOne() {
        for (s in listOf(HighContrastLight, HighContrastDark)) {
            for ((fg, bg) in listOf(s.onSurface to s.surface, s.onPrimary to s.primary, s.primary to s.surface, s.outline to s.surface, s.onSecondaryContainer to s.secondaryContainer)) {
                assertTrue("${contrast(fg, bg)}", contrast(fg, bg) >= 7.0) // WCAG AAA
            }
        }
    }

    @Test
    fun brandSchemesMeetWcagAa() {
        // Design roadmap D58: Fern's own colours, light and dark.
        for (s in listOf(FernLight, FernDark)) {
            val text = listOf(
                s.onSurface to s.surface, s.onSurfaceVariant to s.surface, s.primary to s.surface,
                s.onPrimary to s.primary, s.onPrimaryContainer to s.primaryContainer,
                s.onSecondaryContainer to s.secondaryContainer, s.onError to s.error,
            )
            for ((fg, bg) in text) assertTrue("${contrast(fg, bg)}", contrast(fg, bg) >= 4.5)
            // Outlines are not text: WCAG 1.4.11 asks 3:1 of control boundaries.
            assertTrue("${contrast(s.outline, s.surface)}", contrast(s.outline, s.surface) >= 3.0)
        }
    }
}
