package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 103: EPS export. */
@RunWith(RobolectricTestRunner::class)
class EpsExportTest {
    @Test
    fun strokesAndFillsBecomePostScriptPathsOnAFramedPage() {
        val state = CanvasState()
        state.loadStrokes(listOf(
            Stroke(Color.Red, 4.0, lineStyle = LineStyle(cap = LineCap.BUTT)).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)) },
            Stroke(Color.Blue, 0.0, filled = true).apply { addPoint(WorldPoint(0.0, 50.0)); addPoint(WorldPoint(50.0, 50.0)); addPoint(WorldPoint(25.0, 90.0)) },
        ))
        val eps = exportDocumentEps(state)!!
        assertTrue(eps.startsWith("%!PS-Adobe-3.0 EPSF-3.0\n"))
        assertTrue(eps.contains("%%BoundingBox: 0 0 "))
        assertEquals(1, Regex(" stroke\n").findAll(eps).count())
        assertEquals(1, Regex(" closepath fill\n").findAll(eps).count())
        assertTrue(eps.contains("1 0 0 setrgbcolor 4 setlinewidth 0 setlinecap"))
        assertTrue(eps.trimEnd().endsWith("%%EOF"))
    }

    @Test
    fun anEmptyDocumentHasNoExport() {
        assertNull(exportDocumentEps(CanvasState()))
    }
}
