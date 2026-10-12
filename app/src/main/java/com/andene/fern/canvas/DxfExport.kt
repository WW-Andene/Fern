package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * The document's strokes as DXF (roadmap item 127), the drawing exchange format CAD programs
 * (AutoCAD, LibreCAD, FreeCAD, Fusion, laser-cutter and plotter software) open. Written as
 * ASCII DXF R12 (AC1009), the version nearly every reader still accepts: each Fern layer is a
 * DXF layer (hidden ones switched off), each stroke a 2D POLYLINE of its width, dashed strokes
 * on the DASHED linetype. Colours are given as the nearest of the nine basic AutoCAD colours
 * (all R12 has) plus the exact colour as a true-colour value, which newer readers use. R12 has
 * no hatches, so fills come out as closed outlines. Units are world units with y pointing up,
 * like the EPS export; text and images aren't included. The current animation frame only.
 * Null for an empty document.
 */
fun exportDocumentDxf(source: CanvasState): String? {
    val frame = frameForExport(source, EXPORT_MAX_EDGE_PX) ?: return null
    val camera = frame.camera
    val center = Offset(frame.widthPx / 2f, frame.heightPx / 2f)
    val min = camera.screenToWorld(Offset.Zero, center)
    val max = camera.screenToWorld(Offset(frame.widthPx.toFloat(), frame.heightPx.toFloat()), center)
    fun x(v: Double) = v - min.x
    fun y(v: Double) = max.y - v

    val names = dxfLayerNames(camera.layers)
    val dxf = StringBuilder()
    fun group(code: Int, value: Any) {
        dxf.append(code).append('\n').append(if (value is Double) n(value) else value.toString()).append('\n')
    }
    group(0, "SECTION"); group(2, "HEADER")
    group(9, "\$ACADVER"); group(1, "AC1009")
    group(9, "\$EXTMIN"); group(10, 0.0); group(20, 0.0)
    group(9, "\$EXTMAX"); group(10, max.x - min.x); group(20, max.y - min.y)
    group(0, "ENDSEC")

    group(0, "SECTION"); group(2, "TABLES")
    group(0, "TABLE"); group(2, "LTYPE"); group(70, 2)
    group(0, "LTYPE"); group(2, "CONTINUOUS"); group(70, 0); group(3, "Solid line"); group(72, 65); group(73, 0); group(40, 0.0)
    group(0, "LTYPE"); group(2, "DASHED"); group(70, 0); group(3, "Dashed __ __ __"); group(72, 65); group(73, 2); group(40, 1.0)
    group(49, 0.5); group(49, -0.5)
    group(0, "ENDTAB")
    group(0, "TABLE"); group(2, "LAYER"); group(70, camera.layers.size)
    for (layer in camera.layers) {
        group(0, "LAYER"); group(2, names.getValue(layer.id)); group(70, 0)
        group(62, if (layer.visible) 7 else -7) // a negative colour switches the layer off
        group(6, "CONTINUOUS")
    }
    group(0, "ENDTAB")
    group(0, "ENDSEC")

    group(0, "SECTION"); group(2, "ENTITIES")
    val layerIndex = camera.layers.withIndex().associate { (index, layer) -> layer.id to index }
    val ordered = camera.strokes.withIndex()
        .filter { it.value.animFrame == camera.currentAnimFrame && it.value.points.isNotEmpty() }
        .sortedWith(compareBy({ layerIndex[it.value.layerId] ?: 0 }, { it.index }))
        .map { it.value }
    for (stroke in ordered) {
        val layerName = names[stroke.layerId] ?: names.getValue(camera.layers.first().id)
        val points = stroke.points
        group(0, "POLYLINE"); group(8, layerName)
        group(62, nearestAci(stroke.color)); group(420, trueColor(stroke.color))
        if (stroke.dashed) group(6, "DASHED")
        group(66, 1) // vertices follow
        group(10, 0.0); group(20, 0.0)
        group(70, if (stroke.filled) 1 else 0) // 1: closed
        if (!stroke.filled) {
            group(40, stroke.widthWorld); group(41, stroke.widthWorld)
        }
        // A dot is a zero-length segment, which width-drawing readers show as a dot.
        val vertices = if (points.size == 1) listOf(points[0], points[0]) else points
        for (p in vertices) {
            group(0, "VERTEX"); group(8, layerName)
            group(10, x(p.x)); group(20, y(p.y))
        }
        group(0, "SEQEND"); group(8, layerName)
    }
    group(0, "ENDSEC")
    group(0, "EOF")
    return dxf.toString()
}

/** DXF layer names for [layers]: the characters DXF forbids replaced, and kept unique. */
internal fun dxfLayerNames(layers: List<Layer>): Map<String, String> {
    val used = HashSet<String>()
    return layers.associate { layer ->
        val base = layer.name.replace(Regex("[<>/\\\\\":;?*|=',]"), "_").trim().ifEmpty { "Layer" }
        var name = base
        var suffix = 2
        while (!used.add(name.uppercase(Locale.ROOT))) name = "$base ${suffix++}"
        layer.id to name
    }
}

// The nine basic AutoCAD Colour Index entries R12 readers all show the same way.
private val BASIC_ACI = listOf(
    1 to Color(1f, 0f, 0f), 2 to Color(1f, 1f, 0f), 3 to Color(0f, 1f, 0f),
    4 to Color(0f, 1f, 1f), 5 to Color(0f, 0f, 1f), 6 to Color(1f, 0f, 1f),
    7 to Color(0f, 0f, 0f), 8 to Color(0.5f, 0.5f, 0.5f), 9 to Color(0.75f, 0.75f, 0.75f),
)

/** The basic AutoCAD colour nearest [color]. 7 is black or white depending on the background, so it stands for black. */
internal fun nearestAci(color: Color): Int = BASIC_ACI.minBy { (_, c) ->
    val dr = c.red - color.red
    val dg = c.green - color.green
    val db = c.blue - color.blue
    dr * dr + dg * dg + db * db
}.first

private fun trueColor(color: Color): Int =
    ((color.red * 255 + 0.5f).toInt() shl 16) or ((color.green * 255 + 0.5f).toInt() shl 8) or (color.blue * 255 + 0.5f).toInt()

private fun n(value: Double): String = String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.').let { if (it.isEmpty() || it == "-0") "0" else it }
