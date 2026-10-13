package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D47: the inspector's measurement of the selection's lines. */
@RunWith(RobolectricTestRunner::class)
class SelectionInspectorTest {
    @Test
    fun outlineLengthAddsEveryStrokesPath() {
        // A 3-4-5 corner and a 10-long line.
        val corner = Stroke(Color.Black, 2.0).apply {
            addPoint(WorldPoint(0.0, 0.0))
            addPoint(WorldPoint(3.0, 4.0))
        }
        val line = Stroke(Color.Black, 2.0).apply {
            addPoint(WorldPoint(0.0, 0.0))
            addPoint(WorldPoint(10.0, 0.0))
        }
        assertEquals(15.0, outlineLength(listOf(corner, line)), 1e-9)
        assertEquals(0.0, outlineLength(emptyList()), 0.0)
    }
}
