package com.andene.fern.canvas

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * The user's saved signature (roadmap item 129): drawn once on the signature pad and placed on
 * any document after that, the way PDF and note apps sign. App-wide, outside any document:
 * strokes in the binary stroke format ([StrokeCodec]) in files/signature.bin, in the pad's own
 * coordinates; [placedSignature] sizes a copy for the canvas.
 */
object SignatureStore {
    private fun file(context: Context) = File(context.filesDir, "signature.bin")

    /** The saved signature's strokes, or empty when none was saved (or it can't be read). */
    fun load(context: Context): List<Stroke> {
        val file = file(context)
        if (!file.exists()) return emptyList()
        return try {
            StrokeCodec.read(DataInputStream(ByteArrayInputStream(file.readBytes())))
        } catch (e: IOException) {
            emptyList()
        }
    }

    /** Saves [strokes] as the signature, replacing any earlier one. */
    fun save(context: Context, strokes: List<Stroke>) {
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), strokes) }.toByteArray()
        val atomic = AtomicFile(file(context))
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
            throw e
        }
    }
}

/**
 * Copies of the signature [strokes] scaled so the signature is [width] world units wide, line
 * widths scaled alike, ready for [CanvasState.pasteStrokes]. Empty for an empty signature.
 */
fun placedSignature(strokes: List<Stroke>, width: Double): List<Stroke> {
    val drawn = strokes.filter { it.points.isNotEmpty() }
    if (drawn.isEmpty()) return emptyList()
    val minX = drawn.minOf { it.minX }
    val maxX = drawn.maxOf { it.maxX }
    val factor = width / (maxX - minX).coerceAtLeast(1e-9)
    return drawn.map { stroke ->
        Stroke(stroke.color, stroke.widthWorld * factor, stroke.penType, lineStyle = stroke.lineStyle).apply {
            for (i in stroke.points.indices) {
                val p = stroke.points[i]
                addPoint(WorldPoint(p.x * factor, p.y * factor), stroke.pressures[i], stroke.tilts[i], stroke.orientations[i])
            }
        }
    }
}
