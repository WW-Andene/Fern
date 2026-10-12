package com.andene.fern.canvas

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A high-dynamic-range picture (roadmap item 139): [width] x [height] pixels of linear-light
 * RGBA floats, premultiplied (OpenEXR's convention), row by row from the top. Values may pass
 * 1.0 - brighter than paper white.
 */
class ExrImage(val width: Int, val height: Int, val rgba: FloatArray)

/**
 * OpenEXR (roadmap item 139), the high-dynamic-range format of film, VFX and Krita: single-part
 * scanline files with R, G, B and A channels. [write] stores half floats with ZIP compression
 * (falling back to raw blocks that don't shrink). [read] takes HALF, FLOAT and UINT channels,
 * without compression or with ZIPS or ZIP, and a lone Y channel as grey; tiled, multi-part,
 * deep files and the PIZ, PXR24, B44 and DWA codecs aren't read. Following the specification
 * (openexr.com, "OpenEXR File Layout").
 */
object OpenExr {
    private const val MAGIC = 20000630
    private const val HALF = 1
    private const val FLOAT = 2
    private const val UINT = 0
    const val NO_COMPRESSION = 0
    const val ZIPS_COMPRESSION = 2
    const val ZIP_COMPRESSION = 3

    fun write(image: ExrImage, compression: Int = ZIP_COMPRESSION): ByteArray {
        require(compression == NO_COMPRESSION || compression == ZIPS_COMPRESSION || compression == ZIP_COMPRESSION)
        val w = image.width
        val h = image.height
        val out = ByteArrayOutputStream()
        fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        fun attribute(name: String, type: String, value: ByteArray) {
            out.write(name.toByteArray()); out.write(0)
            out.write(type.toByteArray()); out.write(0)
            out.write(le(4).putInt(value.size).array())
            out.write(value)
        }
        out.write(le(8).putInt(MAGIC).putInt(2).array())
        val channels = ByteArrayOutputStream()
        for (name in CHANNELS_SORTED) {
            channels.write(name.toByteArray()); channels.write(0)
            channels.write(le(16).putInt(HALF).put(0).put(0).put(0).put(0).putInt(1).putInt(1).array())
        }
        channels.write(0)
        attribute("channels", "chlist", channels.toByteArray())
        attribute("compression", "compression", byteArrayOf(compression.toByte()))
        val box = le(16).putInt(0).putInt(0).putInt(w - 1).putInt(h - 1).array()
        attribute("dataWindow", "box2i", box)
        attribute("displayWindow", "box2i", box)
        attribute("lineOrder", "lineOrder", byteArrayOf(0))
        attribute("pixelAspectRatio", "float", le(4).putFloat(1f).array())
        attribute("screenWindowCenter", "v2f", le(8).putFloat(0f).putFloat(0f).array())
        attribute("screenWindowWidth", "float", le(4).putFloat(1f).array())
        out.write(0) // end of header

        val linesPerBlock = linesPerBlock(compression)
        val blocks = (h + linesPerBlock - 1) / linesPerBlock
        val chunks = (0 until blocks).map { b ->
            val y0 = b * linesPerBlock
            val lines = minOf(linesPerBlock, h - y0)
            // Each line: each channel in name order (A, B, G, R), every pixel's value.
            val raw = le(lines * CHANNELS_SORTED.size * w * 2)
            for (y in y0 until y0 + lines) for (channel in CHANNEL_INDEX_SORTED) for (x in 0 until w) {
                raw.putShort(floatToHalf(image.rgba[(y * w + x) * 4 + channel]))
            }
            val bytes = raw.array()
            y0 to if (compression == NO_COMPRESSION) bytes else zipCompress(bytes)
        }
        // Offset table: where each chunk starts, from the start of the file.
        var offset = out.size().toLong() + blocks * 8L
        val table = le(blocks * 8)
        for ((_, data) in chunks) {
            table.putLong(offset)
            offset += 8 + data.size
        }
        out.write(table.array())
        for ((y, data) in chunks) {
            out.write(le(8).putInt(y).putInt(data.size).array())
            out.write(data)
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): ExrImage {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 8 || buf.int != MAGIC) throw IOException("Not an OpenEXR file")
        val version = buf.int
        if (version and 0xFF != 2) throw IOException("OpenEXR version ${version and 0xFF} isn't supported")
        if (version and (0x200 or 0x800 or 0x1000) != 0) throw IOException("Tiled, deep or multi-part OpenEXR files aren't supported")
        fun string(): String {
            val start = buf.position()
            while (bytes[buf.position()].toInt() != 0) buf.position(buf.position() + 1)
            return String(bytes, start, buf.position() - start).also { buf.get() }
        }
        val channels = ArrayList<Pair<String, Int>>()
        var compression = NO_COMPRESSION
        var window = intArrayOf(0, 0, -1, -1)
        while (true) {
            val name = string()
            if (name.isEmpty()) break
            string() // type
            val size = buf.int
            val end = buf.position() + size
            when (name) {
                "channels" -> while (true) {
                    val channel = string()
                    if (channel.isEmpty()) break
                    val type = buf.int
                    buf.position(buf.position() + 4) // pLinear, reserved
                    if (buf.int != 1 || buf.int != 1) throw IOException("Subsampled channels aren't supported")
                    channels.add(channel to type)
                }
                "compression" -> compression = bytes[buf.position()].toInt()
                "dataWindow" -> window = intArrayOf(buf.int, buf.int, buf.int, buf.int)
            }
            buf.position(end)
        }
        if (compression != NO_COMPRESSION && compression != ZIPS_COMPRESSION && compression != ZIP_COMPRESSION) {
            throw IOException("OpenEXR compression $compression isn't supported")
        }
        val w = window[2] - window[0] + 1
        val h = window[3] - window[1] + 1
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_EXR_PIXELS) throw IOException("Bad OpenEXR size")
        val linesPerBlock = linesPerBlock(compression)
        val blocks = (h + linesPerBlock - 1) / linesPerBlock
        val offsets = LongArray(blocks) { buf.long }
        val rgba = FloatArray(w * h * 4)
        // Defaults for missing channels: black, opaque.
        for (i in 0 until w * h) rgba[i * 4 + 3] = 1f
        val sorted = channels.sortedBy { it.first }
        val bytesPer = sorted.map { (_, type) -> if (type == HALF) 2 else 4 }
        val target = sorted.map { (name, _) -> CHANNEL_TARGET[name.substringAfterLast('.')] }
        for (b in 0 until blocks) {
            buf.position(offsets[b].toInt())
            val y0 = buf.int - window[1]
            val size = buf.int
            val lines = minOf(linesPerBlock, h - y0)
            val rawSize = lines * w * bytesPer.sum()
            val data = if (compression == NO_COMPRESSION || size == rawSize) bytes.copyOfRange(buf.position(), buf.position() + size)
            else zipDecompress(bytes, buf.position(), size, rawSize)
            val block = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            for (line in 0 until lines) {
                val y = y0 + line
                for ((k, channel) in sorted.withIndex()) for (x in 0 until w) {
                    val value = when (channel.second) {
                        HALF -> halfToFloat(block.short)
                        FLOAT -> block.float
                        UINT -> (block.int.toLong() and 0xFFFFFFFFL).toFloat()
                        else -> throw IOException("Unknown channel type ${channel.second}")
                    }
                    val t = target[k] ?: continue
                    if (y !in 0 until h) continue
                    if (t == GREY) for (c in 0 until 3) rgba[(y * w + x) * 4 + c] = value
                    else rgba[(y * w + x) * 4 + t] = value
                }
            }
        }
        return ExrImage(w, h, rgba)
    }

    private fun linesPerBlock(compression: Int) = if (compression == ZIP_COMPRESSION) 16 else 1

    // Channels are stored in name order; the image's RGBA index for each.
    private val CHANNELS_SORTED = listOf("A", "B", "G", "R")
    private val CHANNEL_INDEX_SORTED = intArrayOf(3, 2, 1, 0)
    private const val GREY = -1
    private val CHANNEL_TARGET = mapOf("R" to 0, "G" to 1, "B" to 2, "A" to 3, "Y" to GREY)
    private const val MAX_EXR_PIXELS = 64L * 1024 * 1024

    /** ZIP as OpenEXR does it: bytes split into even and odd halves, delta-coded, then zlib. */
    private fun zipCompress(raw: ByteArray): ByteArray {
        val n = raw.size
        val t = ByteArray(n)
        val half = (n + 1) / 2
        for (i in 0 until n) if (i % 2 == 0) t[i / 2] = raw[i] else t[half + i / 2] = raw[i]
        var previous = t[0].toInt()
        for (i in 1 until n) {
            val d = t[i].toInt() - previous + (128 + 256)
            previous = t[i].toInt()
            t[i] = d.toByte()
        }
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(t)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(65536)
        while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
        deflater.end()
        val packed = out.toByteArray()
        return if (packed.size < n) packed else raw
    }

    private fun zipDecompress(bytes: ByteArray, offset: Int, size: Int, rawSize: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes, offset, size)
        val t = ByteArray(rawSize)
        var done = 0
        while (done < rawSize && !inflater.finished()) {
            val n = inflater.inflate(t, done, rawSize - done)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            done += n
        }
        inflater.end()
        if (done != rawSize) throw IOException("Damaged OpenEXR block")
        for (i in 1 until rawSize) t[i] = (t[i - 1].toInt() + t[i].toInt() - 128).toByte()
        val raw = ByteArray(rawSize)
        val half = (rawSize + 1) / 2
        for (i in 0 until rawSize) raw[i] = if (i % 2 == 0) t[i / 2] else t[half + i / 2]
        return raw
    }
}

/** IEEE 754 binary16 bits nearest [value] (round to nearest even), as OpenEXR's HALF stores it. */
fun floatToHalf(value: Float): Short {
    val bits = java.lang.Float.floatToIntBits(value)
    val sign = (bits ushr 16) and 0x8000
    val exponent = ((bits ushr 23) and 0xFF) - 127 + 15
    var mantissa = bits and 0x7FFFFF
    if (((bits ushr 23) and 0xFF) == 0xFF) return (sign or 0x7C00 or (if (mantissa != 0) 0x200 else 0)).toShort() // inf, NaN
    if (exponent >= 0x1F) return (sign or 0x7C00).toShort() // too large: infinity
    if (exponent <= 0) {
        if (exponent < -10) return sign.toShort() // underflows to zero
        mantissa = mantissa or 0x800000
        val shift = 14 - exponent
        var half = mantissa shr shift
        val rest = mantissa and ((1 shl shift) - 1)
        val midpoint = 1 shl (shift - 1)
        if (rest > midpoint || (rest == midpoint && (half and 1) != 0)) half++
        return (sign or half).toShort()
    }
    var half = (exponent shl 10) or (mantissa shr 13)
    val rest = mantissa and 0x1FFF
    if (rest > 0x1000 || (rest == 0x1000 && (half and 1) != 0)) half++ // may carry into the exponent, which is right
    return (sign or half).toShort()
}

/** The float a binary16 value [half] stands for. */
fun halfToFloat(half: Short): Float {
    val h = half.toInt() and 0xFFFF
    val sign = if (h and 0x8000 != 0) -1f else 1f
    val exponent = (h ushr 10) and 0x1F
    val mantissa = h and 0x3FF
    return when (exponent) {
        0 -> sign * mantissa * HALF_SUBNORMAL_STEP
        0x1F -> if (mantissa == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
        else -> sign * java.lang.Float.intBitsToFloat(((exponent - 15 + 127) shl 23) or (mantissa shl 13))
    }
}

private const val HALF_SUBNORMAL_STEP = 5.9604645e-8f // 2^-24

/** sRGB-encoded [v] (0..1) to linear light, and back ([linearToSrgb] takes HDR values past 1 too). */
fun srgbToLinear(v: Float): Float = if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

fun linearToSrgb(v: Float): Float = if (v <= 0.0031308f) v * 12.92f else (1.055 * Math.pow(v.toDouble(), 1 / 2.4) - 0.055).toFloat()


/**
 * An OpenEXR picture as one 16-bit layer to import (roadmap item 139): linear light turned to
 * the sRGB values Fern paints in, unpremultiplied; values past paper white are clipped to it.
 */
fun ExrImage.toImportedDocument(name: String): ImportedDocument {
    val rgba16 = ShortArray(width * height * 4)
    for (i in 0 until width * height) {
        val a = rgba[i * 4 + 3].coerceIn(0f, 1f)
        for (c in 0 until 3) {
            val linear = if (a > 0f) rgba[i * 4 + c] / a else 0f
            rgba16[i * 4 + c] = (linearToSrgb(linear.coerceIn(0f, 1f)) * 65535 + 0.5f).toInt().toShort()
        }
        rgba16[i * 4 + 3] = (a * 65535 + 0.5f).toInt().toShort()
    }
    return ImportedDocument(width, height, 16, listOf(ImportedLayer(name, 0, 0, width, height, rgba16)))
}

/** [argb] (non-premultiplied sRGB, as a rendered bitmap holds it) as linear premultiplied floats for OpenEXR. */
fun exrImageFromArgb(argb: IntArray, width: Int, height: Int): ExrImage {
    val rgba = FloatArray(width * height * 4)
    for (i in 0 until width * height) {
        val c = argb[i]
        val a = (c ushr 24) / 255f
        rgba[i * 4] = srgbToLinear(((c shr 16) and 0xFF) / 255f) * a
        rgba[i * 4 + 1] = srgbToLinear(((c shr 8) and 0xFF) / 255f) * a
        rgba[i * 4 + 2] = srgbToLinear((c and 0xFF) / 255f) * a
        rgba[i * 4 + 3] = a
    }
    return ExrImage(width, height, rgba)
}
