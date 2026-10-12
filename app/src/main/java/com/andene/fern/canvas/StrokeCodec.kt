package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Compact binary encoding of stroke lists, used for the strokes file, undo history and saved
 * versions. JSON spent roughly 90 bytes per point (every point an object with five named
 * fields); this spends 16 (two Doubles) for a finger stroke and 28 for a stylus stroke, and
 * parses without building an object tree, which is what lets a document grow toward a million
 * strokes (roadmap item 18).
 *
 * Layout of one block, all big-endian (`DataOutputStream`):
 * ```
 * int   magic 'FSTK'      int  version
 * int   name count, then that many UTF strings (pen types, blend modes, layer ids)
 * int   stroke count, then per stroke:
 *   int color (ARGB)  double width  short pen-type name  short blend-mode name
 *   int layer-id name  int animation frame  byte flags  int point count
 *   per point: double x, double y [, float pressure, float tilt, float orientation]
 * ```
 * Enum values are stored by name through the name table, not by ordinal, so reordering an enum
 * can't silently change saved strokes; an unknown name (from a newer Fern) falls back to the
 * default, as the JSON reader did. Stylus fields are written only when a stroke has any
 * non-default value ([FLAG_STYLUS]); omitting them is lossless because the defaults (pressure
 * 1, tilt 0, orientation 0) are exactly what was captured.
 */
object StrokeCodec {
    private const val MAGIC = 0x4653544B // "FSTK"

    /** Bump when the layout changes, and keep reading every older version. */
    const val VERSION = 1

    private const val FLAG_FILLED = 1
    private const val FLAG_DASHED = 2
    private const val FLAG_FADE = 4
    private const val FLAG_TONE = 8
    private const val FLAG_STYLUS = 16

    fun write(out: DataOutputStream, strokes: List<Stroke>) {
        val names = LinkedHashMap<String, Int>()
        fun nameIndex(name: String): Int = names.getOrPut(name) { names.size }
        // Build the name table first: it's written before the strokes that refer to it.
        for (stroke in strokes) {
            nameIndex(stroke.penType.name)
            nameIndex(stroke.blendMode.name)
            nameIndex(stroke.layerId)
        }
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(names.size)
        for (name in names.keys) out.writeUTF(name)
        out.writeInt(strokes.size)
        for (stroke in strokes) {
            val stylus = hasStylusData(stroke)
            var flags = 0
            if (stroke.filled) flags = flags or FLAG_FILLED
            if (stroke.dashed) flags = flags or FLAG_DASHED
            if (stroke.fadeFill) flags = flags or FLAG_FADE
            if (stroke.screentone) flags = flags or FLAG_TONE
            if (stylus) flags = flags or FLAG_STYLUS
            out.writeInt(stroke.color.toArgb())
            out.writeDouble(stroke.widthWorld)
            out.writeShort(nameIndex(stroke.penType.name))
            out.writeShort(nameIndex(stroke.blendMode.name))
            out.writeInt(nameIndex(stroke.layerId))
            out.writeInt(stroke.animFrame)
            out.writeByte(flags)
            val points = stroke.points
            out.writeInt(points.size)
            for (i in points.indices) {
                out.writeDouble(points[i].x)
                out.writeDouble(points[i].y)
                if (stylus) {
                    out.writeFloat(stroke.pressures[i])
                    out.writeFloat(stroke.tilts[i])
                    out.writeFloat(stroke.orientations[i])
                }
            }
        }
    }

    /** Reads one block written by [write]; throws [IOException] on a malformed or newer-version block. */
    fun read(input: DataInputStream): List<Stroke> {
        if (input.readInt() != MAGIC) throw IOException("Not a Fern stroke block")
        val version = input.readInt()
        if (version > VERSION) throw IOException("Stroke format $version is newer than supported $VERSION")
        val names = List(input.readInt().also { checkCount(it) }) { input.readUTF() }
        fun name(index: Int): String = names.getOrNull(index) ?: throw IOException("Bad name index $index")
        val count = input.readInt().also { checkCount(it) }
        val strokes = ArrayList<Stroke>(minOf(count, PREALLOCATE_LIMIT))
        repeat(count) {
            val color = Color(input.readInt())
            val width = input.readDouble()
            // Each name is read before searching the enum: reading inside the search lambda would
            // consume a field per enum value tried.
            val penTypeName = name(input.readShort().toInt())
            val blendModeName = name(input.readShort().toInt())
            val penType = enumValues<PenType>().firstOrNull { it.name == penTypeName } ?: PenType.MARKER
            val blendMode = enumValues<StrokeBlendMode>().firstOrNull { it.name == blendModeName } ?: StrokeBlendMode.NORMAL
            val layerId = name(input.readInt())
            val frame = input.readInt()
            val flags = input.readByte().toInt()
            val stroke = Stroke(
                color, width, penType,
                filled = (flags and FLAG_FILLED) != 0,
                blendMode = blendMode,
                layerId = layerId,
                animFrame = frame,
                dashed = (flags and FLAG_DASHED) != 0,
                fadeFill = (flags and FLAG_FADE) != 0,
                screentone = (flags and FLAG_TONE) != 0,
            )
            val stylus = (flags and FLAG_STYLUS) != 0
            repeat(input.readInt().also { checkCount(it) }) {
                val point = WorldPoint(input.readDouble(), input.readDouble())
                if (stylus) stroke.addPoint(point, input.readFloat(), input.readFloat(), input.readFloat())
                else stroke.addPoint(point)
            }
            strokes.add(stroke)
        }
        return strokes
    }

    private fun hasStylusData(stroke: Stroke): Boolean =
        stroke.pressures.any { it != 1f } || stroke.tilts.any { it != 0f } || stroke.orientations.any { it != 0f }

    // A negative count can only come from a damaged file; failing here lets the caller quarantine it.
    private fun checkCount(count: Int) {
        if (count < 0) throw IOException("Negative count $count")
    }

    // Caps the up-front list allocation so a damaged count can't allocate gigabytes before failing.
    private const val PREALLOCATE_LIMIT = 1 shl 16
}
