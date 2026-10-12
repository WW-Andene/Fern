package com.andene.fern.canvas

import android.graphics.BitmapFactory
import java.io.DataInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.math.hypot

/**
 * What a raster brush does to the pixels under it (roadmap item 90):
 * - [PAINT]: lays the brush colour down.
 * - [WET]: paint that mixes - the brush picks up the colour under it and carries it along, so
 *   strokes blend into what they cross, like wet oil or gouache.
 * - [SMUDGE]: drags the pixels under the brush along the stroke, like a finger in wet paint.
 * - [BLUR]: softens the pixels under the brush.
 * - [LIQUIFY]: pushes the picture along the stroke, bending it like soft clay (roadmap item 92).
 */
enum class RasterBrushMode {
    PAINT, WET, SMUDGE, BLUR, LIQUIFY,

    /** Particles scattered over the tip, like an airbrush or spray can (roadmap item 140). */
    SPRAY,

    /** A row of hairs, each streaking and drying out along the stroke (roadmap item 140). */
    BRISTLE,
}

/**
 * The raster brush's settings: its [mode], tip ([tip], a picture as for stamp brushes, or a
 * round tip when null) and, for [RasterBrushMode.WET], how readily it picks up colour
 * ([wetness]) and for [RasterBrushMode.SMUDGE] / [RasterBrushMode.BLUR] how strongly it acts
 * ([strength]).
 */
data class RasterBrush(
    val mode: RasterBrushMode = RasterBrushMode.PAINT,
    val tip: TipMask? = null,
    val wetness: Float = 0.5f,
    val strength: Float = 0.6f,
)

/** Coverage (0..1) of [mask] at ([u], [v]) in -1..1 across the tip, bilinearly sampled. */
fun TipMask.coverageAt(u: Double, v: Double): Double {
    val x = (u + 1) / 2 * (size - 1)
    val y = (v + 1) / 2 * (size - 1)
    if (x < 0 || y < 0 || x > size - 1 || y > size - 1) return 0.0
    val x0 = x.toInt().coerceAtMost(size - 2).coerceAtLeast(0)
    val y0 = y.toInt().coerceAtMost(size - 2).coerceAtLeast(0)
    val fx = x - x0
    val fy = y - y0
    fun at(i: Int, j: Int) = (alpha[j * size + i].toInt() and 0xFF) / 255.0
    val top = at(x0, y0) * (1 - fx) + at(x0 + 1, y0) * fx
    val bottom = at(x0, y0 + 1) * (1 - fx) + at(x0 + 1, y0 + 1) * fx
    return top * (1 - fy) + bottom * fy
}

/**
 * Brush tips from other painting apps (roadmap item 90), as [TipMask]s:
 * - Photoshop `.abr` (versions 6 to 10): every sampled brush in the file's `samp` section;
 *   computed brushes have no picture and are skipped.
 * - Procreate `.brush` (a zip): its `Shape.png`, white being where the tip paints. The grain
 *   and the dynamics stored in `Brush.archive` aren't read.
 */
object BrushImport {
    /** Tips from a file, recognised by its contents; empty if it holds none. */
    fun read(bytes: ByteArray): List<TipMask> = when {
        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> listOfNotNull(procreateShape(bytes))
        else -> abr(bytes)
    }

    private fun procreateShape(bytes: ByteArray): TipMask? {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.substringAfterLast('/').equals("Shape.png", ignoreCase = true)) {
                    val png = zip.readBytes()
                    val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    // Procreate shapes are white on black: invert so the usual darkness rule reads white as ink.
                    for (i in pixels.indices) pixels[i] = pixels[i] xor 0x00FFFFFF
                    return tipMaskFromArgb(pixels, bitmap.width, bitmap.height)
                }
            }
        }
        return null
    }

    /** Photoshop brushes, following the layout GIMP's ABR loader documents. */
    internal fun abr(bytes: ByteArray): List<TipMask> {
        val input = DataInputStream(bytes.inputStream())
        val version = input.readShort().toInt()
        if (version !in 6..10) throw IOException("ABR version $version isn't supported (6 to 10 are)")
        val subversion = input.readShort().toInt()
        val tips = ArrayList<TipMask>()
        // Sections: "8BIM" signature, a 4-letter key, a length; only "samp" holds brush pictures.
        while (input.available() >= 12) {
            val signature = String(ByteArray(4).also { input.readFully(it) })
            if (signature != "8BIM") break
            val key = String(ByteArray(4).also { input.readFully(it) })
            val length = input.readInt()
            if (length < 0 || length > input.available()) break
            val section = ByteArray(length).also { input.readFully(it) }
            if (key == "samp") tips += abrSamples(section, subversion)
            if (length % 2 != 0 && input.available() > 0) input.skipBytes(1)
        }
        return tips
    }

    private fun abrSamples(section: ByteArray, subversion: Int): List<TipMask> {
        val input = DataInputStream(section.inputStream())
        val tips = ArrayList<TipMask>()
        while (input.available() >= 4) {
            val size = input.readInt()
            var padded = size
            while (padded % 4 != 0) padded++
            if (size <= 0 || padded > input.available()) break
            val brush = ByteArray(padded).also { input.readFully(it) }
            runCatching { abrSample(brush, subversion) }.getOrNull()?.let { tips.add(it) }
        }
        return tips
    }

    private fun abrSample(brush: ByteArray, subversion: Int): TipMask? {
        val input = DataInputStream(brush.inputStream())
        input.skipBytes(if (subversion == 1) 47 else 301)
        val top = input.readInt()
        val left = input.readInt()
        val bottom = input.readInt()
        val right = input.readInt()
        val depth = input.readShort().toInt()
        val compressed = input.readByte().toInt() != 0
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0 || width > MAX_ABR_EDGE || height > MAX_ABR_EDGE || depth != 8) return null
        val pixels = ByteArray(width * height)
        if (!compressed) {
            input.readFully(pixels)
        } else {
            // PackBits per row, after a table of each row's compressed length.
            repeat(height) { input.readShort() }
            var at = 0
            while (at < pixels.size) {
                val n = input.readByte().toInt()
                if (n >= 0) {
                    repeat(n + 1) { if (at < pixels.size) pixels[at++] = input.readByte() }
                } else if (n != -128) {
                    val value = input.readByte()
                    repeat(1 - n) { if (at < pixels.size) pixels[at++] = value }
                }
            }
        }
        // ABR samples are opacity: 255 paints fully. As ARGB with that opacity as alpha.
        val argb = IntArray(pixels.size) { ((pixels[it].toInt() and 0xFF) shl 24) }
        return tipMaskFromArgb(argb, width, height)
    }

    // Larger than any brush Photoshop makes (2500 px); guards against a corrupt size.
    private const val MAX_ABR_EDGE = 5000
}

/** Distance helper for brush falloffs. */
internal fun dabFalloff(px: Double, py: Double, cx: Double, cy: Double, r: Double, hardness: Double): Double {
    val d = hypot(px - cx, py - cy) / r
    if (d >= 1.0) return 0.0
    val inner = hardness.coerceIn(0.0, 0.999)
    return if (d <= inner) 1.0 else 1.0 - (d - inner) / (1.0 - inner)
}
