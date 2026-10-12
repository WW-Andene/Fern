package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GimpPaletteTest {
    @Test
    fun parsesColoursAndSkipsMetadata() {
        val text = """
            GIMP Palette
            Name: Test
            Columns: 2
            # a comment
            255   0   0 Red
              0 128 255
            999 0 0 out of range
        """.trimIndent()
        assertEquals(listOf(Color(255, 0, 0), Color(0, 128, 255)), parseGimpPalette(text))
    }

    @Test
    fun missingHeaderGivesNothing() {
        assertTrue(parseGimpPalette("255 0 0").isEmpty())
    }
}
