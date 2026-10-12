package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** Roadmap item 103: Illustrator (PDF-compatible) export, and PDF / AI vector import. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiAndPdfVectorTest {
    private fun line(color: Color, vararg points: WorldPoint, filled: Boolean = false, layer: String = DEFAULT_LAYER_ID) =
        Stroke(color, 4.0, filled = filled, layerId = layer).apply { for (p in points) addPoint(p) }

    /** A PDF file from [objects] (bodies, numbered from 1), with a correct cross-reference table. */
    private fun pdf(vararg objects: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("%PDF-1.5\n".toByteArray())
        val offsets = ArrayList<Int>()
        for ((i, body) in objects.withIndex()) {
            offsets.add(out.size())
            out.write("${i + 1} 0 obj\n".toByteArray())
            out.write(body)
            out.write("\nendobj\n".toByteArray())
        }
        val xref = out.size()
        out.write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".toByteArray())
        for (o in offsets) out.write("%010d 00000 n \n".format(o).toByteArray())
        out.write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".toByteArray())
        return out.toByteArray()
    }

    private fun stream(content: String, compress: Boolean = true): ByteArray {
        val data = if (compress) deflate(content.toByteArray(Charsets.ISO_8859_1)) else content.toByteArray(Charsets.ISO_8859_1)
        val filter = if (compress) " /Filter /FlateDecode" else ""
        return "<< /Length ${data.size}$filter >>\nstream\n".toByteArray() + data + "\nendstream".toByteArray()
    }

    @Test
    fun aiExportRoundTripsThroughTheVectorReader() {
        val state = CanvasState()
        state.loadStrokes(listOf(
            line(Color.Red, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), WorldPoint(100.0, 50.0)),
            line(Color.Blue.copy(alpha = 0.5f), WorldPoint(0.0, 0.0), WorldPoint(50.0, 0.0), WorldPoint(50.0, 50.0), WorldPoint(0.0, 0.0), filled = true),
        ))
        state.updateLayer(DEFAULT_LAYER_ID) { it.copy(name = "Ink é") }
        val bytes = exportDocumentAi(state)!!
        assertTrue(String(bytes, 0, 8, Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
        val page = readPdfVectors(bytes)!!.single()
        // The paper rectangle on "Paper", then the two strokes on the layer named as in Fern.
        assertEquals(listOf("Paper", "Ink é", "Ink é"), page.paths.map { it.layer })
        val red = page.paths[1]
        assertEquals(Color.Red, red.stroke)
        assertNull(red.fill)
        assertEquals(4.0, red.width, 1e-6)
        val blue = page.paths[2]
        assertEquals(0.5f, blue.fill!!.alpha, 0.01f)
        assertEquals(0f, blue.fill!!.red, 0.01f)
        // Lengths survive: the red line's first leg is 100 points.
        val (points, _) = red.subpaths.single()
        assertEquals(100.0, abs(points[1].x - points[0].x), 1e-3)
    }

    @Test
    fun readsCurvesTransformsRectanglesFillRulesAndLayers() {
        val content = """
            /OC /MC0 BDC
            q 2 0 0 2 10 10 cm 1 0 0 rg 0 0 10 10 re f Q
            EMC
            0 0 1 RG 3 w 1 J 0 0 m 0 100 100 100 100 0 c S
            0 1 0 rg 0 0 m 100 0 l 100 100 l 0 100 l h 25 25 m 75 25 l 75 75 l 25 75 l h f*
            BT /F1 12 Tf (ignored) Tj ET
        """.trimIndent()
        val bytes = pdf(
            "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(),
            "<< /Type /Pages /Kids [3 0 R] /Count 1 /MediaBox [0 0 200 300] >>".toByteArray(),
            "<< /Type /Page /Parent 2 0 R /Contents 4 0 R /Resources << /Properties << /MC0 5 0 R >> >> >>".toByteArray(),
            stream(content),
            "<< /Type /OCG /Name (Shapes) >>".toByteArray(),
        )
        val page = readPdfVectors(bytes)!!.single()
        assertEquals(200.0, page.width, 1e-9) // inherited media box
        assertEquals(300.0, page.height, 1e-9)
        assertEquals(3, page.paths.size)
        val square = page.paths[0]
        assertEquals("Shapes", square.layer)
        assertEquals(Color.Red, square.fill)
        // Scaled 2x and moved by (10, 10): the far corner at (30, 30).
        assertTrue(square.subpaths.single().first.any { abs(it.x - 30.0) < 1e-9 && abs(it.y - 30.0) < 1e-9 })
        val curve = page.paths[1]
        assertNull(curve.layer)
        assertEquals(LineCap.ROUND, curve.cap)
        assertTrue(curve.subpaths.single().first.size > 4) // flattened
        // The curve peaks at y = 75 (3/4 of its control height) halfway along.
        assertEquals(75.0, curve.subpaths.single().first.maxOf { it.y }, 0.5)
        // The even-odd square with a hole fills to 100 x 100 - 50 x 50.
        val ring = page.paths[2]
        assertTrue(ring.evenOdd)
        val outlines = importedFillOutlines(ring.subpaths.map { it.first }, ring.evenOdd)
        assertEquals(7500.0, outlines.sumOf { polygonArea(it) }, 1.0)
    }

    @Test
    fun readsObjectStreamsAndRejectsEncryptedFiles() {
        // Objects 1-3 packed in an object stream (object 5); the content stream is object 4.
        val packed = "<< /Type /Catalog /Pages 2 0 R >> << /Type /Pages /Kids [3 0 R] /Count 1 >> << /Type /Page /Parent 2 0 R /MediaBox [0 0 50 50] /Contents 4 0 R >>"
        val first = packed.indexOf("<< /Type /Pages")
        val third = packed.indexOf("<< /Type /Page ")
        val header = "1 0 2 $first 3 $third "
        val body = deflate((header + packed).toByteArray())
        val objStm = "<< /Type /ObjStm /N 3 /First ${header.length} /Length ${body.size} /Filter /FlateDecode >>\nstream\n".toByteArray() + body + "\nendstream".toByteArray()
        val bytes = pdf("null".toByteArray(), "null".toByteArray(), "null".toByteArray(), stream("0 0 m 50 50 l S", compress = false), objStm)
            .let { String(it, Charsets.ISO_8859_1) }
            // Objects 1-3 written as "null" placeholders would shadow the packed ones: drop them.
            .replace(Regex("[123] 0 obj\nnull\nendobj\n"), "")
            .toByteArray(Charsets.ISO_8859_1)
        val page = readPdfVectors(bytes)!!.single()
        assertEquals(50.0, page.width, 1e-9)
        assertEquals(1, page.paths.size)

        val encrypted = String(pdf("<< /Type /Catalog >>".toByteArray()), Charsets.ISO_8859_1).replace("/Root 1 0 R", "/Root 1 0 R /Encrypt << /Filter /Standard >>")
        assertNull(readPdfVectors(encrypted.toByteArray(Charsets.ISO_8859_1)))
        assertNull(readPdfVectors("not a pdf".toByteArray()))
    }

    @Test
    fun importingPlacesGroupedStrokesOnNamedLayersUndoably() {
        val source = CanvasState()
        source.loadStrokes(listOf(line(Color.Red, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))))
        source.updateLayer(DEFAULT_LAYER_ID) { it.copy(name = "Outlines") }
        val pages = readPdfVectors(exportDocumentAi(source)!!)!!
        val target = CanvasState()
        val added = target.importVectorPages(pages)
        assertEquals(2, added) // the paper rectangle and the line
        assertNotNull(target.layers.firstOrNull { it.name == "Outlines" })
        assertNotNull(target.layers.firstOrNull { it.name == "Paper" })
        assertEquals(1, target.strokes.map { it.groupId }.distinct().size)
        val imported = target.strokes.single { !it.filled }
        assertEquals(Color.Red, imported.color)
        assertEquals(100.0, imported.maxX - imported.minX, 1e-3)
        target.undo()
        assertTrue(target.strokes.isEmpty())
    }
}
