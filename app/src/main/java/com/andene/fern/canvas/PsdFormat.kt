package com.andene.fern.canvas

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A layer read from another painting app's file (roadmap item 95): its pixels as 16-bit RGBA,
 * non-premultiplied, row by row ([width] x [height]), placed at ([left], [top]) in the document's
 * pixels, with its settings.
 */
class ImportedLayer(
    val name: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val rgba16: ShortArray,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blend: LayerBlend = LayerBlend.NORMAL,
)

/** A layered file read back: the canvas size, its bit depth (8 or 16) and layers bottom first. */
class ImportedDocument(val width: Int, val height: Int, val depth: Int, val layers: List<ImportedLayer>)

/** Photoshop's four-letter keys for the layer blend modes. */
private val PSD_BLEND_KEYS = mapOf(
    LayerBlend.NORMAL to "norm", LayerBlend.DISSOLVE to "diss", LayerBlend.DARKEN to "dark", LayerBlend.MULTIPLY to "mul ",
    LayerBlend.COLOR_BURN to "idiv", LayerBlend.LINEAR_BURN to "lbrn", LayerBlend.DARKER_COLOR to "dkCl", LayerBlend.LIGHTEN to "lite",
    LayerBlend.SCREEN to "scrn", LayerBlend.COLOR_DODGE to "div ", LayerBlend.LINEAR_DODGE to "lddg", LayerBlend.LIGHTER_COLOR to "lgCl",
    LayerBlend.OVERLAY to "over", LayerBlend.SOFT_LIGHT to "sLit", LayerBlend.HARD_LIGHT to "hLit", LayerBlend.VIVID_LIGHT to "vLit",
    LayerBlend.LINEAR_LIGHT to "lLit", LayerBlend.PIN_LIGHT to "pLit", LayerBlend.HARD_MIX to "hMix", LayerBlend.DIFFERENCE to "diff",
    LayerBlend.EXCLUSION to "smud", LayerBlend.SUBTRACT to "fsub", LayerBlend.DIVIDE to "fdiv", LayerBlend.HUE to "hue ",
    LayerBlend.SATURATION to "sat ", LayerBlend.COLOR to "colr", LayerBlend.LUMINOSITY to "lum ",
)

/**
 * Photoshop documents (roadmap item 95), version 1 (.psd), RGB, 8 bits per channel when
 * written. [write] stores each layer of a [LayeredImage] (name, visibility, opacity, blend mode,
 * transparency) uncompressed plus the flattened picture, which is what other apps show as a
 * preview. [read] takes RGB documents at 8 or 16 bits with raw or RLE (PackBits) channels;
 * layer masks, adjustment layers, groups and effects aren't read.
 */
object PsdFormat {
    fun write(image: LayeredImage): ByteArray {
        val out = ByteArrayOutputStream()
        val psd = DataOutputStream(out)
        val w = image.width
        val h = image.height
        psd.writeBytes("8BPS")
        psd.writeShort(1)
        psd.write(ByteArray(6))
        psd.writeShort(4) // merged image: RGB + transparency
        psd.writeInt(h)
        psd.writeInt(w)
        psd.writeShort(8)
        psd.writeShort(3) // RGB
        psd.writeInt(0) // colour mode data
        psd.writeInt(0) // image resources

        val info = ByteArrayOutputStream()
        val layerInfo = DataOutputStream(info)
        layerInfo.writeShort(-image.layers.size) // negative: the merged image's first alpha is transparency
        val channelData = ByteArrayOutputStream()
        for (layer in image.layers) {
            layerInfo.writeInt(0); layerInfo.writeInt(0); layerInfo.writeInt(h); layerInfo.writeInt(w)
            layerInfo.writeShort(4)
            for (id in intArrayOf(-1, 0, 1, 2)) {
                layerInfo.writeShort(id)
                layerInfo.writeInt(2 + w * h)
            }
            layerInfo.writeBytes("8BIM")
            layerInfo.writeBytes(PSD_BLEND_KEYS.getValue(layer.blend))
            layerInfo.writeByte((layer.opacity * 255 + 0.5f).toInt().coerceIn(0, 255))
            layerInfo.writeByte(0) // clipping: base
            layerInfo.writeByte(if (layer.visible) 0 else 2) // flags: bit 1 = hidden
            layerInfo.writeByte(0)
            val name = layer.name.toByteArray(Charsets.ISO_8859_1).take(255).toByteArray()
            val namePadded = (1 + name.size + 3) / 4 * 4
            layerInfo.writeInt(4 + 4 + namePadded) // extra data: mask, blending ranges, name
            layerInfo.writeInt(0)
            layerInfo.writeInt(0)
            layerInfo.writeByte(name.size)
            layerInfo.write(name)
            layerInfo.write(ByteArray(namePadded - 1 - name.size))
            val ch = DataOutputStream(channelData)
            for (shift in intArrayOf(24, 16, 8, 0)) {
                ch.writeShort(0) // raw
                ch.write(ByteArray(w * h) { (layer.pixels[it] ushr shift).toByte() })
            }
        }
        layerInfo.write(channelData.toByteArray())
        var layerBytes = info.toByteArray()
        if (layerBytes.size % 2 != 0) layerBytes += 0
        psd.writeInt(4 + layerBytes.size + 4) // layer and mask information
        psd.writeInt(layerBytes.size)
        psd.write(layerBytes)
        psd.writeInt(0) // global layer mask

        psd.writeShort(0) // merged image, raw, planar R, G, B, A
        for (shift in intArrayOf(16, 8, 0, 24)) psd.write(ByteArray(w * h) { (image.merged[it] ushr shift).toByte() })
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): ImportedDocument {
        val input = DataInputStream(bytes.inputStream())
        if (String(ByteArray(4).also { input.readFully(it) }, Charsets.ISO_8859_1) != "8BPS") throw IOException("Not a Photoshop file")
        val version = input.readShort().toInt()
        if (version != 1) throw IOException("Only .psd (version 1) is supported, not .psb")
        input.skipBytes(6)
        input.readShort() // channels of the merged image
        val height = input.readInt()
        val width = input.readInt()
        val depth = input.readShort().toInt()
        val mode = input.readShort().toInt()
        if (mode != 3) throw IOException("Only RGB documents are supported")
        if (depth != 8 && depth != 16) throw IOException("Only 8 or 16 bits per channel are supported")
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) throw IOException("Bad size")
        input.skipBytes(input.readInt()) // colour mode data
        input.skipBytes(input.readInt()) // image resources
        val layerAndMaskLength = input.readInt()
        if (layerAndMaskLength == 0) return ImportedDocument(width, height, depth, emptyList())
        val section = ByteArray(layerAndMaskLength).also { input.readFully(it) }
        val s = DataInputStream(section.inputStream())
        val infoLength = s.readInt()
        if (infoLength == 0) return ImportedDocument(width, height, depth, emptyList())
        val count = kotlin.math.abs(s.readShort().toInt())

        class Record(val top: Int, val left: Int, val bottom: Int, val right: Int, val channels: List<Pair<Int, Int>>, val blend: String, val opacity: Int, val flags: Int, val name: String)
        val records = (0 until count).map {
            val top = s.readInt(); val left = s.readInt(); val bottom = s.readInt(); val right = s.readInt()
            val channels = List(s.readShort().toInt()) { s.readShort().toInt() to s.readInt() }
            s.skipBytes(4) // "8BIM"
            val blend = String(ByteArray(4).also { s.readFully(it) }, Charsets.ISO_8859_1)
            val opacity = s.readUnsignedByte()
            s.readUnsignedByte() // clipping
            val flags = s.readUnsignedByte()
            s.readUnsignedByte()
            val extra = ByteArray(s.readInt()).also { s.readFully(it) }
            val e = DataInputStream(extra.inputStream())
            e.skipBytes(e.readInt()) // mask
            e.skipBytes(e.readInt()) // blending ranges
            val nameLength = e.readUnsignedByte()
            val name = String(ByteArray(nameLength).also { e.readFully(it) }, Charsets.ISO_8859_1)
            Record(top, left, bottom, right, channels, blend, opacity, flags, name)
        }
        val bytesPerSample = depth / 8
        val layers = records.mapNotNull { r ->
            val w = r.right - r.left
            val h = r.bottom - r.top
            val planes = HashMap<Int, ShortArray>()
            for ((id, length) in r.channels) {
                val data = ByteArray(length).also { s.readFully(it) }
                if (w <= 0 || h <= 0) continue
                planes[id] = decodeChannel(data, w, h, bytesPerSample)
            }
            if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return@mapNotNull null
            val rgba = ShortArray(w * h * 4)
            for (i in 0 until w * h) {
                for ((c, id) in intArrayOf(0, 1, 2, -1).withIndex()) rgba[i * 4 + c] = planes[id]?.get(i) ?: if (id == -1) (-1).toShort() else 0
            }
            ImportedLayer(
                r.name, r.left, r.top, w, h, rgba,
                visible = r.flags and 2 == 0,
                opacity = r.opacity / 255f,
                blend = PSD_BLEND_KEYS.entries.firstOrNull { it.value == r.blend }?.key ?: LayerBlend.NORMAL,
            )
        }
        return ImportedDocument(width, height, depth, layers)
    }

    /** One channel's samples as 16-bit values: raw, or PackBits per row after a table of row lengths. */
    private fun decodeChannel(data: ByteArray, w: Int, h: Int, bytesPerSample: Int): ShortArray {
        val input = DataInputStream(data.inputStream())
        val compression = input.readShort().toInt()
        val raw = when (compression) {
            0 -> ByteArray(w * h * bytesPerSample).also { input.readFully(it) }
            1 -> {
                repeat(h) { input.readShort() }
                val out = ByteArray(w * h * bytesPerSample)
                var at = 0
                while (at < out.size && input.available() > 0) {
                    val n = input.readByte().toInt()
                    if (n >= 0) repeat(n + 1) { if (at < out.size) out[at++] = input.readByte() }
                    else if (n != -128) {
                        val v = input.readByte()
                        repeat(1 - n) { if (at < out.size) out[at++] = v }
                    }
                }
                out
            }
            else -> throw IOException("Channel compression $compression isn't supported")
        }
        return ShortArray(w * h) { i ->
            if (bytesPerSample == 1) ((raw[i].toInt() and 0xFF) * 257).toShort()
            else (((raw[i * 2].toInt() and 0xFF) shl 8) or (raw[i * 2 + 1].toInt() and 0xFF)).toShort()
        }
    }

    // A guard against a corrupt size (30000 x 30000 is Photoshop's own limit for .psd).
    private const val MAX_PIXELS = 30_000L * 30_000L
}
