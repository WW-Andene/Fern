package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * The document as an Adobe Illustrator file (roadmap item 103): a PDF-compatible .ai, the form
 * Illustrator itself has saved since version 9 and that Illustrator, Inkscape and Affinity
 * Designer open as editable vectors. Without Illustrator's private data, which isn't
 * documented, it is the PDF part alone. Each visible Fern layer becomes a PDF layer (an
 * optional content group), which those apps open as layers of the same name; the paper is a
 * rectangle on its own "Paper" layer at the bottom. As in the EPS and CMYK PDF
 * exports: the current animation frame, polylines with their caps, joins and dashes, fills as
 * filled paths (solid colour; fades, screentones and gradients as their base colour), stroke
 * opacity kept, one point per world unit. Text and images aren't included. Null for an empty
 * document.
 */
fun exportDocumentAi(source: CanvasState): ByteArray? {
    val frame = frameForExport(source, EXPORT_MAX_EDGE_PX) ?: return null
    val camera = frame.camera
    val center = Offset(frame.widthPx / 2f, frame.heightPx / 2f)
    val min = camera.screenToWorld(Offset.Zero, center)
    val max = camera.screenToWorld(Offset(frame.widthPx.toFloat(), frame.heightPx.toFloat()), center)
    val width = max.x - min.x
    val height = max.y - min.y
    fun x(v: Double) = pdfNumber(v - min.x)
    fun y(v: Double) = pdfNumber(max.y - v) // PDF's y axis points up
    fun rgb(color: Color) = "${pdfNumber(color.red.toDouble())} ${pdfNumber(color.green.toDouble())} ${pdfNumber(color.blue.toDouble())}"

    // One graphics state per opacity in use, named by its percentage.
    val opacities = sortedSetOf<Int>()
    fun opacity(color: Color): String {
        val percent = Math.round(color.alpha * 100)
        if (percent >= 100) return ""
        opacities.add(percent)
        return "/GS$percent gs "
    }

    val pdf = PdfWriter()
    val catalog = pdf.reserve()
    val pages = pdf.reserve()
    val page = pdf.reserve()
    // Layers: the paper, then Fern's layers bottom first, each a group its content is marked with.
    val layerGroups = ArrayList<Pair<String, Int>>() // property name, OCG object
    val hiddenGroups = ArrayList<Int>()
    val content = StringBuilder()
    fun beginLayer(name: String): String {
        val ocg = pdf.add("<< /Type /OCG /Name ${pdfTextString(name)} >>")
        val property = "L${layerGroups.size}"
        layerGroups.add(property to ocg)
        return property
    }
    content.append("/OC /${beginLayer(PAPER_LAYER_NAME)} BDC q ${rgb(camera.backgroundColor)} rg 0 0 ${pdfNumber(width)} ${pdfNumber(height)} re f Q EMC\n")
    for (layer in camera.layers) {
        val strokes = camera.strokes.filter { it.layerId == layer.id && it.animFrame == camera.currentAnimFrame && it.points.isNotEmpty() }
        if (strokes.isEmpty()) continue
        val property = beginLayer(layer.name)
        if (!layer.visible) hiddenGroups.add(layerGroups.last().second)
        content.append("/OC /$property BDC\n")
        for (stroke in strokes) {
            val points = stroke.points
            content.append("q ").append(opacity(stroke.color))
            content.append("${x(points[0].x)} ${y(points[0].y)} m")
            for (i in 1 until points.size) content.append(" ${x(points[i].x)} ${y(points[i].y)} l")
            if (points.size == 1) content.append(" ${x(points[0].x)} ${y(points[0].y)} l") // a dot: a zero-length round-capped line
            if (stroke.filled) {
                content.append(" h ${rgb(stroke.color)} rg f Q\n")
            } else {
                // PDF caps: 0 butt, 1 round, 2 square; joins: 0 miter, 1 round, 2 bevel.
                val cap = when (stroke.lineStyle?.cap) { LineCap.BUTT -> 0; LineCap.SQUARE -> 2; else -> 1 }
                val join = when (stroke.lineStyle?.join) { LineJoin.MITER -> 0; LineJoin.BEVEL -> 2; else -> 1 }
                val dash = if (stroke.dashed) "[${pdfNumber(stroke.widthWorld * 3)} ${pdfNumber(stroke.widthWorld * 2)}] 0 d" else "[] 0 d"
                content.append(" ${rgb(stroke.color)} RG ${pdfNumber(stroke.widthWorld)} w $cap J $join j $dash S Q\n")
            }
        }
        content.append("EMC\n")
    }

    val stream = deflate(content.toString().toByteArray(Charsets.ISO_8859_1))
    val contents = pdf.add("<< /Length ${stream.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.ISO_8859_1) + stream + "\nendstream".toByteArray(Charsets.ISO_8859_1))
    val states = opacities.joinToString(" ") { percent -> "/GS$percent << /Type /ExtGState /CA ${pdfNumber(percent / 100.0)} /ca ${pdfNumber(percent / 100.0)} >>" }
    val properties = layerGroups.joinToString(" ") { (name, ocg) -> "/$name $ocg 0 R" }
    val groups = layerGroups.joinToString(" ") { "${it.second} 0 R" }
    // Listed top layer first, as layer panels show them.
    val order = layerGroups.reversed().joinToString(" ") { "${it.second} 0 R" }
    val off = hiddenGroups.joinToString(" ") { "$it 0 R" }
    pdf.set(catalog, "<< /Type /Catalog /Pages $pages 0 R /OCProperties << /OCGs [$groups] /D << /Order [$order] /OFF [$off] >> >> >>")
    pdf.set(pages, "<< /Type /Pages /Kids [$page 0 R] /Count 1 >>")
    pdf.set(page, "<< /Type /Page /Parent $pages 0 R /MediaBox [0 0 ${pdfNumber(width)} ${pdfNumber(height)}] /Resources << /ExtGState << $states >> /Properties << $properties >> >> /Contents $contents 0 R >>")
    return pdf.write(catalog)
}

/**
 * [text] as a PDF text string: a literal string (backslash and parentheses escaped) when it is
 * all printable Latin-1, otherwise UTF-16BE with a byte order mark, in hex - the two encodings
 * PDF text strings allow (ISO 32000-1, 7.9.2.2).
 */
internal fun pdfTextString(text: String): String {
    if (text.all { it.code in 0x20..0x7E || it.code in 0xA0..0xFF }) {
        return "(" + text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"
    }
    return "<FEFF" + text.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it.toInt() and 0xFF) } + ">"
}

// The bottom layer the paper rectangle goes on.
private const val PAPER_LAYER_NAME = "Paper"
