package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Deflater

/**
 * The document's strokes as a print-ready PDF in CMYK (roadmap item 102) - the platform's PDF
 * writer is RGB-only, so this writes the file itself (PDF 1.4, ISO 32000-1's structure: catalog,
 * page tree, one page, a compressed content stream, an xref table). Like the EPS export: the
 * current animation frame, in layer order, polylines with their caps, joins and dashes, fills as
 * filled paths, the paper as a background, one point per world unit. Colours are converted to
 * ink amounts through the loaded ICC profile ([ColorManagement]) or as device CMYK; a stroke in
 * a spot colour ([spots], matched by its screen colour) is painted in that ink's Separation
 * colour space, which a RIP outputs on its own plate (falling back to the spot's CMYK mix).
 * Transparency, text and images aren't included. Null for an empty document.
 */
fun exportDocumentCmykPdf(source: CanvasState, spots: List<SpotColor> = ColorManagement.spotColors.toList()): ByteArray? {
    val frame = frameForExport(source, EXPORT_MAX_EDGE_PX) ?: return null
    val camera = frame.camera
    val center = Offset(frame.widthPx / 2f, frame.heightPx / 2f)
    val min = camera.screenToWorld(Offset.Zero, center)
    val max = camera.screenToWorld(Offset(frame.widthPx.toFloat(), frame.heightPx.toFloat()), center)
    val width = max.x - min.x
    val height = max.y - min.y
    fun x(v: Double) = n(v - min.x)
    fun y(v: Double) = n(max.y - v) // PDF's y axis points up

    // Spot colours in use, each a named Separation colour space.
    val spotNames = LinkedHashMap<Int, Pair<String, SpotColor>>()
    fun spotOf(color: Color): Pair<String, SpotColor>? {
        val rgb = color.copy(alpha = 1f).let { c -> spots.firstOrNull { it.color.copy(alpha = 1f) == c } } ?: return null
        val key = spots.indexOf(rgb)
        return spotNames.getOrPut(key) { "CS${spotNames.size + 1}" to rgb }
    }
    fun ink(color: Color, fill: Boolean): String {
        spotOf(color)?.let { (name, _) -> return if (fill) "/$name cs 1 scn" else "/$name CS 1 SCN" }
        val (c, m, yy, k) = cmykOf(color)
        return "${n(c.toDouble())} ${n(m.toDouble())} ${n(yy.toDouble())} ${n(k.toDouble())} ${if (fill) "k" else "K"}"
    }

    val content = StringBuilder()
    content.append("${ink(camera.backgroundColor, fill = true)} 0 0 ${n(width)} ${n(height)} re f\n")
    val layerIndex = camera.layers.withIndex().associate { (index, layer) -> layer.id to index }
    val ordered = camera.strokes.withIndex()
        .filter { it.value.animFrame == camera.currentAnimFrame && it.value.points.isNotEmpty() }
        .sortedWith(compareBy({ layerIndex[it.value.layerId] ?: 0 }, { it.index }))
        .map { it.value }
    for (stroke in ordered) {
        val layer = camera.layers.getOrNull(layerIndex[stroke.layerId] ?: 0)
        if (layer != null && !layer.visible) continue
        val points = stroke.points
        content.append("${x(points[0].x)} ${y(points[0].y)} m")
        for (i in 1 until points.size) content.append(" ${x(points[i].x)} ${y(points[i].y)} l")
        if (points.size == 1) content.append(" ${x(points[0].x)} ${y(points[0].y)} l") // a dot: a zero-length round-capped line
        if (stroke.filled) {
            content.append(" h ${ink(stroke.color, fill = true)} f\n")
        } else {
            // PDF caps: 0 butt, 1 round, 2 square; joins: 0 miter, 1 round, 2 bevel.
            val cap = when (stroke.lineStyle?.cap) { LineCap.BUTT -> 0; LineCap.SQUARE -> 2; else -> 1 }
            val join = when (stroke.lineStyle?.join) { LineJoin.MITER -> 0; LineJoin.BEVEL -> 2; else -> 1 }
            val dash = if (stroke.dashed) "[${n(stroke.widthWorld * 3)} ${n(stroke.widthWorld * 2)}] 0 d" else "[] 0 d"
            content.append(" ${ink(stroke.color, fill = false)} ${n(stroke.widthWorld)} w $cap J $join j $dash S\n")
        }
    }

    val pdf = PdfWriter()
    val catalog = pdf.reserve()
    val pages = pdf.reserve()
    val page = pdf.reserve()
    val stream = deflate(content.toString().toByteArray(Charsets.ISO_8859_1))
    val contents = pdf.add("<< /Length ${stream.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.ISO_8859_1) + stream + "\nendstream".toByteArray(Charsets.ISO_8859_1))
    // Each spot: a Separation space whose tint transform blends from no ink to its CMYK mix.
    val colorSpaces = spotNames.values.joinToString(" ") { (name, spot) ->
        val (c, m, yy, k) = spot.cmyk
        val function = pdf.add("<< /FunctionType 2 /Domain [0 1] /C0 [0 0 0 0] /C1 [${n(c.toDouble())} ${n(m.toDouble())} ${n(yy.toDouble())} ${n(k.toDouble())}] /N 1 >>")
        "/$name [/Separation /${pdfName(spot.name)} /DeviceCMYK $function 0 R]"
    }
    pdf.set(catalog, "<< /Type /Catalog /Pages $pages 0 R >>")
    pdf.set(pages, "<< /Type /Pages /Kids [$page 0 R] /Count 1 >>")
    pdf.set(page, "<< /Type /Page /Parent $pages 0 R /MediaBox [0 0 ${n(width)} ${n(height)}] /Resources << /ColorSpace << $colorSpaces >> >> /Contents $contents 0 R >>")
    return pdf.write(catalog)
}

/** [name] as a PDF name: characters outside the printable ASCII range, and delimiters, written as #xx. */
internal fun pdfName(name: String): String = buildString {
    for (b in name.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        if (c in 0x21..0x7E && c.toChar() !in "#()<>[]{}/%") append(c.toChar()) else append("#%02X".format(c))
    }
}

/** A minimal PDF file builder: numbered objects, then a cross-reference table and trailer. */
private class PdfWriter {
    private val objects = ArrayList<ByteArray?>()

    fun reserve(): Int {
        objects.add(null)
        return objects.size
    }

    fun set(number: Int, body: String) {
        objects[number - 1] = body.toByteArray(Charsets.ISO_8859_1)
    }

    fun add(body: String): Int = add(body.toByteArray(Charsets.ISO_8859_1))

    fun add(body: ByteArray): Int {
        objects.add(body)
        return objects.size
    }

    fun write(root: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun put(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        put("%PDF-1.4\n%âãÏÓ\n") // a binary comment marks the file as binary
        val offsets = IntArray(objects.size)
        for ((i, body) in objects.withIndex()) {
            offsets[i] = out.size()
            put("${i + 1} 0 obj\n")
            out.write(body ?: error("PDF object ${i + 1} was never written"))
            put("\nendobj\n")
        }
        val xref = out.size()
        put("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (offset in offsets) put(String.format(Locale.ROOT, "%010d 00000 n \n", offset))
        put("trailer\n<< /Size ${objects.size + 1} /Root $root 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}

private fun deflate(bytes: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    deflater.setInput(bytes)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(65536)
    while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
    deflater.end()
    return out.toByteArray()
}

private fun n(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.').let { if (it.isEmpty() || it == "-0") "0" else it }
