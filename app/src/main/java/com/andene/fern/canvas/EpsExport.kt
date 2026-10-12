package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * The document's strokes as Encapsulated PostScript (roadmap item 103), the vector format print
 * shops and older design tools (Illustrator, InDesign, CorelDRAW) open. Like the SVG export:
 * the current animation frame, in layer order, uniform-width polylines with their caps and
 * joins, fills as filled paths (non-zero), the paper as a background rectangle. PostScript has
 * no transparency, so opacity is left out (colours drawn opaque); text, images and gradients
 * aren't included. Null for an empty document.
 */
fun exportDocumentEps(source: CanvasState): String? {
    val frame = frameForExport(source, EXPORT_MAX_EDGE_PX) ?: return null
    val camera = frame.camera
    val center = Offset(frame.widthPx / 2f, frame.heightPx / 2f)
    val min = camera.screenToWorld(Offset.Zero, center)
    val max = camera.screenToWorld(Offset(frame.widthPx.toFloat(), frame.heightPx.toFloat()), center)
    // One point per world unit, the page the framed area; PostScript's y axis points up.
    val width = max.x - min.x
    val height = max.y - min.y
    fun x(v: Double) = n(v - min.x)
    fun y(v: Double) = n(max.y - v)

    val eps = StringBuilder()
    eps.append("%!PS-Adobe-3.0 EPSF-3.0\n")
    eps.append("%%BoundingBox: 0 0 ${kotlin.math.ceil(width).toInt()} ${kotlin.math.ceil(height).toInt()}\n")
    eps.append("%%HiResBoundingBox: 0 0 ${n(width)} ${n(height)}\n")
    eps.append("%%Creator: Fern\n%%EndComments\n")
    eps.append("${rgb(camera.backgroundColor)} setrgbcolor 0 0 ${n(width)} ${n(height)} rectfill\n")

    val layerIndex = camera.layers.withIndex().associate { (index, layer) -> layer.id to index }
    val ordered = camera.strokes.withIndex()
        .filter { it.value.animFrame == camera.currentAnimFrame && it.value.points.isNotEmpty() }
        .sortedWith(compareBy({ layerIndex[it.value.layerId] ?: 0 }, { it.index }))
        .map { it.value }
    for (stroke in ordered) {
        val layer = camera.layers.getOrNull(layerIndex[stroke.layerId] ?: 0)
        if (layer != null && !layer.visible) continue
        val points = stroke.points
        eps.append("newpath ${x(points[0].x)} ${y(points[0].y)} moveto")
        for (i in 1 until points.size) eps.append(" ${x(points[i].x)} ${y(points[i].y)} lineto")
        eps.append(" ${rgb(stroke.color)} setrgbcolor")
        if (stroke.filled) {
            eps.append(" closepath fill\n")
        } else {
            // PostScript caps: 0 butt, 1 round, 2 square; joins: 0 miter, 1 round, 2 bevel.
            val cap = when (stroke.lineStyle?.cap) { LineCap.BUTT -> 0; LineCap.SQUARE -> 2; else -> 1 }
            val join = when (stroke.lineStyle?.join) { LineJoin.MITER -> 0; LineJoin.BEVEL -> 2; else -> 1 }
            eps.append(" ${n(stroke.widthWorld)} setlinewidth $cap setlinecap $join setlinejoin")
            if (stroke.dashed) eps.append(" [${n(stroke.widthWorld * 3)} ${n(stroke.widthWorld * 3)}] 0 setdash") else eps.append(" [] 0 setdash")
            if (points.size == 1) eps.append(" ${x(points[0].x)} ${y(points[0].y)} lineto") // a dot: a zero-length round-capped line
            eps.append(" stroke\n")
        }
    }
    eps.append("showpage\n%%EOF\n")
    return eps.toString()
}

private fun rgb(color: Color) = "${n(color.red.toDouble())} ${n(color.green.toDouble())} ${n(color.blue.toDouble())}"

private fun n(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
