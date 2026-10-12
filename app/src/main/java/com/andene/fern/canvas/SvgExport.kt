package com.andene.fern.canvas

import android.util.Base64
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * The whole document as an SVG string, or null if it's empty. Coordinates are world units,
 * so the file is resolution-independent and opens editable in vector tools (Illustrator,
 * Inkscape, Figma).
 *
 * Simplified compared with the canvas: strokes are uniform-width polylines (no per-point
 * pressure, pen-type texture or blend mode, which SVG strokes can't express directly), and
 * the paper pattern is omitted; the paper colour is kept as a background rect.
 * [imageBytesFor] returns an image file's bytes to embed, or null to skip it.
 */
fun exportDocumentSvg(source: CanvasState, imageBytesFor: (String) -> ByteArray?): String? {
    val frame = frameForExport(source, EXPORT_MAX_EDGE_PX) ?: return null
    val camera = frame.camera
    // The viewBox is the framed world-space rect, so the SVG keeps world units.
    val center = androidx.compose.ui.geometry.Offset(frame.widthPx / 2f, frame.heightPx / 2f)
    val min = camera.screenToWorld(androidx.compose.ui.geometry.Offset.Zero, center)
    val max = camera.screenToWorld(androidx.compose.ui.geometry.Offset(frame.widthPx.toFloat(), frame.heightPx.toFloat()), center)
    val width = max.x - min.x
    val height = max.y - min.y

    val svg = StringBuilder()
    svg.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="${n(min.x)} ${n(min.y)} ${n(width)} ${n(height)}" """)
    svg.append("""width="${frame.widthPx}" height="${frame.heightPx}">""").append('\n')
    svg.append("""<rect x="${n(min.x)}" y="${n(min.y)}" width="${n(width)}" height="${n(height)}" fill="${hex(camera.backgroundColor)}"/>""").append('\n')

    for (item in camera.imageItems) {
        val bytes = imageBytesFor(item.fileName) ?: continue
        val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        svg.append("""<image x="${n(item.position.x)}" y="${n(item.position.y)}" width="${n(item.widthWorld)}" height="${n(item.heightWorld)}" """)
        svg.append("""href="data:image/png;base64,$data"/>""").append('\n')
    }

    // Same layer order and visibility as the canvas. Layer opacity is folded into each stroke's
    // opacity, which only differs from the canvas where strokes on one layer overlap.
    val layerIndex = camera.layers.withIndex().associate { (index, layer) -> layer.id to index }
    val ordered = camera.strokes.withIndex()
        .sortedWith(compareBy({ layerIndex[it.value.layerId] ?: 0 }, { it.index }))
        .map { it.value }
    for (stroke in ordered) {
        if (stroke.points.isEmpty()) continue
        val layer = camera.layers.getOrNull(layerIndex[stroke.layerId] ?: 0)
        if (layer != null && !layer.visible) continue
        val d = stroke.points.mapIndexed { i, p -> (if (i == 0) "M" else "L") + "${n(p.x)} ${n(p.y)}" }.joinToString(" ")
        val opacity = n(stroke.color.alpha.toDouble() * (layer?.opacity ?: 1f))
        if (stroke.filled) {
            svg.append("""<path d="$d Z" fill="${hex(stroke.color)}" fill-opacity="$opacity"/>""")
        } else {
            svg.append("""<path d="$d" fill="none" stroke="${hex(stroke.color)}" stroke-opacity="$opacity" """)
            svg.append("""stroke-width="${n(stroke.widthWorld)}" stroke-linecap="round" stroke-linejoin="round"/>""")
        }
        svg.append('\n')
    }

    for (item in camera.textItems) {
        // SVG text is positioned by its baseline; the canvas anchors text at its top-left.
        val baseline = item.position.y + item.fontSizeWorld
        svg.append("""<text x="${n(item.position.x)}" y="${n(baseline)}" font-size="${n(item.fontSizeWorld)}" """)
        svg.append("""fill="${hex(item.color)}" font-family="sans-serif"""")
        if (item.bold) svg.append(""" font-weight="bold"""")
        if (item.italic) svg.append(""" font-style="italic"""")
        svg.append(""">${escapeXml(item.text)}</text>""").append('\n')
    }
    svg.append("</svg>\n")
    return svg.toString()
}

/** Compact, locale-independent number formatting (a comma decimal separator would break the SVG). */
private fun n(value: Double): String = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

private fun hex(color: Color): String = String.format(
    Locale.US, "#%02x%02x%02x",
    (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(),
)

private fun escapeXml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
