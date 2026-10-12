package com.andene.fern

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text/background pair in the high-contrast schemes clears WCAG AA (4.5:1) by far. */
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
}
