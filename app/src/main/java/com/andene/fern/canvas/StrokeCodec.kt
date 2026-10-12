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
 *   int layer-id name  int animation frame  byte flags  [byte flags2 (version 5+)]
 *   [if FLAG_BRUSH: short tip name  float spacing  float scatter  float size jitter
 *                   float angle jitter  byte follow-direction]          (version 2+)
 *   [if FLAG_GRADIENT: int end colour (ARGB)  short angle in degrees]  (version 3+)
 *   [if FLAG_GROUP: int group-id name]                                   (version 4+)
 *   [if FLAG2_BONE: int bone-id name]                                    (version 5+)
 *   [if FLAG2_STYLE: byte packed line style (LineStyle.pack)]            (version 5+)
 *   [if FLAG2_TIP_MASK: short mask size, then size x size coverage bytes] (version 6+)
 *   [if FLAG2_WEIGHTS: after the points, one float joint weight per point] (version 7+)
 *   int point count
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

    /**
     * Bump when the layout changes, and keep reading every older version.
     * 1: first binary format. 2: optional stamp-brush settings per stroke ([FLAG_BRUSH]).
     * 3: optional two-colour fill gradient ([FLAG_GRADIENT]).
     * 4: optional group id ([FLAG_GROUP]); flags are read as an unsigned byte from here on.
     * 5: a second flags byte, its first flag an optional bone id ([FLAG2_BONE]).
     * 6: optional image-tip picture for a stamp brush ([FLAG2_TIP_MASK]).
     * 7: optional painted joint weights ([FLAG2_WEIGHTS]).
     */
    const val VERSION = 7

    private const val FLAG_FILLED = 1
    private const val FLAG_DASHED = 2
    private const val FLAG_FADE = 4
    private const val FLAG_TONE = 8
    private const val FLAG_STYLUS = 16
    private const val FLAG_BRUSH = 32
    private const val FLAG_GRADIENT = 64
    // The last bit of the flags byte: a new flag needs a wider flags field and a version bump.
    private const val FLAG_GROUP = 128
    // Second flags byte (version 5+).
    private const val FLAG2_BONE = 1
    private const val FLAG2_STYLE = 2
    private const val FLAG2_TIP_MASK = 4
    private const val FLAG2_WEIGHTS = 8
    // Guards against a corrupt size allocating a huge array; real masks are TIP_MASK_SIZE.
    private const val MAX_TIP_MASK_SIZE = 512

    fun write(out: DataOutputStream, strokes: List<Stroke>) {
        val names = LinkedHashMap<String, Int>()
        fun nameIndex(name: String): Int = names.getOrPut(name) { names.size }
        // Build the name table first: it's written before the strokes that refer to it.
        for (stroke in strokes) {
            nameIndex(stroke.penType.name)
            nameIndex(stroke.blendMode.name)
            nameIndex(stroke.layerId)
            stroke.brush?.let { nameIndex(it.tip.name) }
            stroke.groupId?.let { nameIndex(it) }
            stroke.boneId?.let { nameIndex(it) }
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
            val brush = stroke.brush
            if (brush != null) flags = flags or FLAG_BRUSH
            val gradient = stroke.gradient
            if (gradient != null) flags = flags or FLAG_GRADIENT
            val groupId = stroke.groupId
            if (groupId != null) flags = flags or FLAG_GROUP
            out.writeInt(stroke.color.toArgb())
            out.writeDouble(stroke.widthWorld)
            out.writeShort(nameIndex(stroke.penType.name))
            out.writeShort(nameIndex(stroke.blendMode.name))
            out.writeInt(nameIndex(stroke.layerId))
            out.writeInt(stroke.animFrame)
            out.writeByte(flags)
            val boneId = stroke.boneId
            val lineStyle = stroke.lineStyle
            val mask = brush?.mask
            val weights = stroke.jointWeights?.takeIf { it.size == stroke.points.size }
            out.writeByte(
                (if (boneId != null) FLAG2_BONE else 0) or (if (lineStyle != null) FLAG2_STYLE else 0) or
                    (if (mask != null) FLAG2_TIP_MASK else 0) or (if (weights != null) FLAG2_WEIGHTS else 0),
            )
            if (brush != null) {
                out.writeShort(nameIndex(brush.tip.name))
                out.writeFloat(brush.spacing)
                out.writeFloat(brush.scatter)
                out.writeFloat(brush.sizeJitter)
                out.writeFloat(brush.angleJitter)
                out.writeBoolean(brush.followDirection)
            }
            if (gradient != null) {
                out.writeInt(gradient.endColor.toArgb())
                out.writeShort(gradient.angleDegrees)
            }
            if (groupId != null) out.writeInt(nameIndex(groupId))
            if (boneId != null) out.writeInt(nameIndex(boneId))
            if (lineStyle != null) out.writeByte(lineStyle.pack())
            if (mask != null) {
                out.writeShort(mask.size)
                out.write(mask.alpha)
            }
            val points = stroke.points
            out.writeInt(points.size)
            for (i in points.indices) {
                out.writeDouble(points[i].x)
                out.writeDouble(points[i].y)
                if (stylus) {
                    out.writeFloat(stroke.pressureAt(i))
                    out.writeFloat(stroke.tiltAt(i))
                    out.writeFloat(stroke.orientationAt(i))
                }
            }
            if (weights != null) for (w in weights) out.writeFloat(w)
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
            val flags = input.readUnsignedByte()
            val flags2 = if (version >= 5) input.readUnsignedByte() else 0
            val brush = if ((flags and FLAG_BRUSH) != 0) {
                val tipName = name(input.readShort().toInt())
                StampBrush(
                    tip = enumValues<StampTip>().firstOrNull { it.name == tipName } ?: StampTip.CIRCLE,
                    spacing = input.readFloat(),
                    scatter = input.readFloat(),
                    sizeJitter = input.readFloat(),
                    angleJitter = input.readFloat(),
                    followDirection = input.readBoolean(),
                ).clamped()
            } else {
                null
            }
            val gradient = if ((flags and FLAG_GRADIENT) != 0) {
                FillGradient(Color(input.readInt()), input.readShort().toInt())
            } else {
                null
            }
            val groupId = if ((flags and FLAG_GROUP) != 0) name(input.readInt()) else null
            val boneId = if ((flags2 and FLAG2_BONE) != 0) name(input.readInt()) else null
            val lineStyle = if ((flags2 and FLAG2_STYLE) != 0) LineStyle.unpack(input.readUnsignedByte()) else null
            val mask = if ((flags2 and FLAG2_TIP_MASK) != 0) {
                val size = input.readUnsignedShort()
                if (size == 0 || size > MAX_TIP_MASK_SIZE) throw IOException("Bad tip mask size $size")
                TipMask(size, ByteArray(size * size).also { input.readFully(it) })
            } else {
                null
            }
            val stroke = Stroke(
                color, width, penType,
                filled = (flags and FLAG_FILLED) != 0,
                blendMode = blendMode,
                layerId = layerId,
                animFrame = frame,
                dashed = (flags and FLAG_DASHED) != 0,
                fadeFill = (flags and FLAG_FADE) != 0,
                screentone = (flags and FLAG_TONE) != 0,
                brush = if (mask != null) brush?.copy(mask = mask) else brush,
                gradient = gradient,
                groupId = groupId,
                boneId = boneId,
                lineStyle = lineStyle,
            )
            val stylus = (flags and FLAG_STYLUS) != 0
            repeat(input.readInt().also { checkCount(it) }) {
                val point = WorldPoint(input.readDouble(), input.readDouble())
                if (stylus) stroke.addPoint(point, input.readFloat(), input.readFloat(), input.readFloat())
                else stroke.addPoint(point)
            }
            if ((flags2 and FLAG2_WEIGHTS) != 0) stroke.setJointWeights(FloatArray(stroke.points.size) { input.readFloat() })
            strokes.add(stroke)
        }
        return strokes
    }

    private fun hasStylusData(stroke: Stroke): Boolean =
        (0 until stroke.points.size).any { stroke.pressureAt(it) != 1f || stroke.tiltAt(it) != 0f || stroke.orientationAt(it) != 0f }

    // A negative count can only come from a damaged file; failing here lets the caller quarantine it.
    private fun checkCount(count: Int) {
        if (count < 0) throw IOException("Negative count $count")
    }

    // Caps the up-front list allocation so a damaged count can't allocate gigabytes before failing.
    private const val PREALLOCATE_LIMIT = 1 shl 16
}
