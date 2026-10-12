package com.andene.fern.canvas

import android.util.Base64
import androidx.compose.ui.geometry.Offset
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
    var gradientCount = 0
    for (stroke in ordered) {
        if (stroke.points.isEmpty()) continue
        val layer = camera.layers.getOrNull(layerIndex[stroke.layerId] ?: 0)
        if (layer != null && !layer.visible) continue
        val d = stroke.points.mapIndexed { i, p -> (if (i == 0) "M" else "L") + "${n(p.x)} ${n(p.y)}" }.joinToString(" ")
        val opacity = n(stroke.color.alpha.toDouble() * (layer?.opacity ?: 1f))
        val gradient = stroke.gradient
        if (stroke.filled && gradient != null) {
            // Same endpoints as the canvas, in world space, so the gradient's angle isn't
            // distorted by the fill's aspect ratio the way objectBoundingBox units would be.
            val (start, end) = gradientEndpoints(stroke.points.map { Offset(it.x.toFloat(), it.y.toFloat()) }, gradient.angleDegrees)
            val id = "g${gradientCount++}"
            svg.append("""<defs><linearGradient id="$id" gradientUnits="userSpaceOnUse" x1="${n(start.x.toDouble())}" y1="${n(start.y.toDouble())}" x2="${n(end.x.toDouble())}" y2="${n(end.y.toDouble())}">""")
            svg.append("""<stop offset="0" stop-color="${hex(stroke.color)}" stop-opacity="$opacity"/>""")
            svg.append("""<stop offset="1" stop-color="${hex(gradient.endColor)}" stop-opacity="${n(gradient.endColor.alpha.toDouble() * (layer?.opacity ?: 1f))}"/>""")
            svg.append("""</linearGradient></defs><path d="$d Z" fill="url(#$id)"/>""")
        } else if (stroke.filled) {
            svg.append("""<path d="$d Z" fill="${hex(stroke.color)}" fill-opacity="$opacity"/>""")
        } else {
            svg.append("""<path d="$d" fill="none" stroke="${hex(stroke.color)}" stroke-opacity="$opacity" """)
            svg.append("""stroke-width="${n(stroke.widthWorld)}" stroke-linecap="round" stroke-linejoin="round"""")
            if (stroke.dashed) svg.append(""" stroke-dasharray="${n(stroke.widthWorld * 3)} ${n(stroke.widthWorld * 3)}"""")
            svg.append("/>")
        }
        svg.append('\n')
    }

    for (item in camera.textItems) {
        // SVG text is positioned by its baseline; the canvas anchors text at its top-left. Each
        // line is its own tspan at 1.3 em spacing (the canvas's approximate line height).
        // Alignment anchors to the box's edges or centre when there is a box, otherwise to the
        // label's left edge. SVG 1.1 has no automatic wrapping, so a boxed label's text keeps
        // only its own line breaks here - the one way this export differs from the canvas.
        val baseline = item.position.y + item.fontSizeWorld
        val box = item.boxWidthWorld
        val x = when {
            box == null -> item.position.x
            item.alignment == TextAlignment.CENTER -> item.position.x + box / 2
            item.alignment == TextAlignment.END -> item.position.x + box
            else -> item.position.x
        }
        svg.append("""<text x="${n(x)}" y="${n(baseline)}" font-size="${n(item.fontSizeWorld)}" """)
        svg.append("""fill="${hex(item.color)}" font-family="${item.font.svgFamily}"""")
        if (box != null) svg.append(""" text-anchor="${item.alignment.svgAnchor}"""")
        if (item.bold) svg.append(""" font-weight="bold"""")
        if (item.italic) svg.append(""" font-style="italic"""")
        svg.append(">")
        item.text.split('\n').forEachIndexed { i, line ->
            val dy = if (i == 0) "0" else n(item.fontSizeWorld * 1.3)
            svg.append("""<tspan x="${n(x)}" dy="$dy">${escapeXml(line)}</tspan>""")
        }
        svg.append("</text>").append('\n')
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
