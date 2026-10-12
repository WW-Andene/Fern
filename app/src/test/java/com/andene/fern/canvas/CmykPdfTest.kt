package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.zip.Inflater

/** Roadmap item 102: the CMYK print PDF, spot colours as separations. */
@RunWith(RobolectricTestRunner::class)
class CmykPdfTest {
    @Test
    fun strokesAreCmykAndSpotsAreSeparations() {
        val state = CanvasState()
        val gold = Color(0.8f, 0.6f, 0.1f)
        state.loadStrokes(listOf(
            Stroke(Color.Red, 4.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)) },
            Stroke(gold, 4.0).apply { addPoint(WorldPoint(0.0, 50.0)); addPoint(WorldPoint(100.0, 50.0)) },
        ))
        val pdf = exportDocumentCmykPdf(state, listOf(SpotColor("Gold 871", gold, listOf(0.2f, 0.3f, 0.9f, 0.1f))))!!
        val text = String(pdf, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("/Separation /Gold#20871 /DeviceCMYK"))
        val start = text.indexOf("stream\n") + 7
        val end = text.indexOf("\nendstream")
        val inflater = Inflater().apply { setInput(pdf, start, end - start) }
        val out = ByteArray(1 shl 16)
        val content = String(out, 0, inflater.inflate(out), Charsets.ISO_8859_1)
        assertTrue(content.contains("0 1 1 0 K")) // red as device CMYK
        assertTrue(content.contains("/CS1 CS 1 SCN")) // gold on its own plate
        // The xref offset points at "xref".
        val startxref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertEquals("xref", text.substring(startxref, startxref + 4))
    }
}
