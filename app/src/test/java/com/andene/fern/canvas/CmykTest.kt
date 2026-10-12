package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap item 102: uncalibrated CMYK entry converts both ways. */
class CmykTest {
    @Test
    fun primariesAndBlackConvert() {
        assertEquals(listOf(0f, 1f, 1f, 0f), cmykOf(Color.Red))
        assertEquals(listOf(0f, 0f, 0f, 1f), cmykOf(Color.Black))
        assertEquals(listOf(0f, 0f, 0f, 0f), cmykOf(Color.White))
        assertEquals(Color.Cyan, colorOfCmyk(listOf(1f, 0f, 0f, 0f)))
    }

    @Test
    fun aMixedColourRoundTrips() {
        val color = Color(0.2f, 0.5f, 0.7f)
        val back = colorOfCmyk(cmykOf(color))
        assertEquals(color.red, back.red, 0.01f)
        assertEquals(color.green, back.green, 0.01f)
        assertEquals(color.blue, back.blue, 0.01f)
    }
}
