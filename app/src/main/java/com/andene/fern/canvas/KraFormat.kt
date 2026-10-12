package com.andene.fern.canvas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Krita's composite-op names for the layer blend modes. */
private val KRITA_BLEND_OPS = mapOf(
    LayerBlend.NORMAL to "normal", LayerBlend.DISSOLVE to "dissolve", LayerBlend.DARKEN to "darken", LayerBlend.MULTIPLY to "multiply",
    LayerBlend.COLOR_BURN to "burn", LayerBlend.LINEAR_BURN to "linear_burn", LayerBlend.DARKER_COLOR to "darker color", LayerBlend.LIGHTEN to "lighten",
    LayerBlend.SCREEN to "screen", LayerBlend.COLOR_DODGE to "dodge", LayerBlend.LINEAR_DODGE to "linear_dodge", LayerBlend.LIGHTER_COLOR to "lighter color",
    LayerBlend.OVERLAY to "overlay", LayerBlend.SOFT_LIGHT to "soft_light", LayerBlend.HARD_LIGHT to "hard_light", LayerBlend.VIVID_LIGHT to "vivid_light",
    LayerBlend.LINEAR_LIGHT to "linear light", LayerBlend.PIN_LIGHT to "pin_light", LayerBlend.HARD_MIX to "hard mix", LayerBlend.DIFFERENCE to "diff",
    LayerBlend.EXCLUSION to "exclusion", LayerBlend.SUBTRACT to "subtract", LayerBlend.DIVIDE to "divide", LayerBlend.HUE to "hue",
    LayerBlend.SATURATION to "saturation", LayerBlend.COLOR to "color", LayerBlend.LUMINOSITY to "luminize",
)

/**
 * Krita documents (.kra, roadmap item 95): a zip of `maindoc.xml` (the layer stack) and each
 * paint layer's pixels in Krita's tiled format - 64 x 64 tiles, each either raw or LZF
 * compressed, with the colour channels stored one after another (blue, green, red, alpha) -
 * plus `mergedimage.png` and `preview.png`. Written at 8 bits per channel; read at 8 bits
 * (RGBA). Group, vector, filter and fill layers, and masks, aren't read.
 */
object KraFormat {
    private const val TILE = 64
    private const val DOC = "fern"

    fun write(image: LayeredImage): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // Krita expects the mimetype first and stored uncompressed, as in OpenDocument files.
            val mime = "application/x-krita".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime)
            zip.closeEntry()
            val xml = StringBuilder()
            xml.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            xml.append("""<!DOCTYPE DOC PUBLIC '-//KDE//DTD krita 2.0//EN' 'http://www.calligra.org/DTD/krita-2.0.dtd'>""").append('\n')
            xml.append("""<DOC xmlns="http://www.calligra.org/DTD/krita" syntaxVersion="2" kritaVersion="5.2.0" editor="Fern">""").append('\n')
            xml.append("""<IMAGE name="$DOC" mime="application/x-kra" width="${image.width}" height="${image.height}" colorspacename="RGBA" profile="sRGB-elle-V2-srgbtrc.icc" x-res="72" y-res="72">""").append('\n')
            xml.append("<layers>\n")
            // Krita lists the top layer first.
            for ((i, layer) in image.layers.withIndex().reversed()) {
                xml.append("""<layer name="${escape(layer.name)}" filename="layer$i" nodetype="paintlayer" visible="${if (layer.visible) 1 else 0}" """)
                xml.append("""opacity="${(layer.opacity * 255).toInt()}" compositeop="${KRITA_BLEND_OPS.getValue(layer.blend)}" colorspacename="RGBA" x="0" y="0" locked="0" collapsed="0" channelflags=""/>""")
                xml.append('\n')
            }
            xml.append("</layers>\n</IMAGE>\n</DOC>\n")
            put(zip, "maindoc.xml", xml.toString().toByteArray())
            for ((i, layer) in image.layers.withIndex()) put(zip, "$DOC/layers/layer$i", tiles(layer.pixels, image.width, image.height))
            put(zip, "mergedimage.png", png(image.merged, image.width, image.height))
            put(zip, "preview.png", png(image.merged, image.width, image.height, maxEdge = 256))
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun png(argb: IntArray, w: Int, h: Int, maxEdge: Int = 0): ByteArray {
        var bitmap = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)
        if (maxEdge > 0 && maxOf(w, h) > maxEdge) {
            val scale = maxEdge.toFloat() / maxOf(w, h)
            bitmap = Bitmap.createScaledBitmap(bitmap, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** A layer in Krita's tile format: only tiles with any paint, each stored raw (flag 0), channels one after another. */
    private fun tiles(argb: IntArray, w: Int, h: Int): ByteArray {
        val body = ByteArrayOutputStream()
        var count = 0
        for (ty in 0 until (h + TILE - 1) / TILE) for (tx in 0 until (w + TILE - 1) / TILE) {
            val data = ByteArray(TILE * TILE * 4)
            var painted = false
            for (y in 0 until TILE) for (x in 0 until TILE) {
                val px = tx * TILE + x
                val py = ty * TILE + y
                if (px >= w || py >= h) continue
                val c = argb[py * w + px]
                if (c ushr 24 != 0) painted = true
                val i = y * TILE + x
                data[i] = c.toByte() // blue
                data[TILE * TILE + i] = (c shr 8).toByte() // green
                data[2 * TILE * TILE + i] = (c shr 16).toByte() // red
                data[3 * TILE * TILE + i] = (c ushr 24).toByte() // alpha
            }
            if (!painted) continue
            count++
            body.write("${tx * TILE},${ty * TILE},LZF,${data.size + 1}\n".toByteArray())
            body.write(0) // raw, not LZF-compressed
            body.write(data)
        }
        return ("VERSION 2\nTILEWIDTH $TILE\nTILEHEIGHT $TILE\nPIXELSIZE 4\nDATA $count\n").toByteArray() + body.toByteArray()
    }

    fun read(bytes: ByteArray): ImportedDocument {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            }
        }
        val doc = entries["maindoc.xml"] ?: throw IOException("Not a Krita document")
        val parser = Xml.newPullParser()
        parser.setInput(doc.inputStream(), "UTF-8")
        var width = 0
        var height = 0
        var imageName = ""
        val layers = ArrayList<ImportedLayer>()
        var depth = 0 // nesting: only the top level's layers are read
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.name == "layers") depth--
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "IMAGE" -> if (imageName.isEmpty()) {
                    imageName = parser.getAttributeValue(null, "name").orEmpty()
                    width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                    height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                }
                "layers" -> depth++
                "layer" -> {
                    if (depth != 1 || parser.getAttributeValue(null, "nodetype") != "paintlayer") continue
                    if (parser.getAttributeValue(null, "colorspacename") != "RGBA") continue
                    val file = entries["$imageName/layers/${parser.getAttributeValue(null, "filename")}"] ?: continue
                    val x = parser.getAttributeValue(null, "x")?.toIntOrNull() ?: 0
                    val y = parser.getAttributeValue(null, "y")?.toIntOrNull() ?: 0
                    val op = parser.getAttributeValue(null, "compositeop")
                    readTiles(file, width, height, x, y)?.let { rgba ->
                        layers.add(
                            ImportedLayer(
                                parser.getAttributeValue(null, "name").orEmpty(), 0, 0, width, height, rgba,
                                visible = parser.getAttributeValue(null, "visible") != "0",
                                opacity = (parser.getAttributeValue(null, "opacity")?.toIntOrNull() ?: 255) / 255f,
                                blend = KRITA_BLEND_OPS.entries.firstOrNull { it.value == op }?.key ?: LayerBlend.NORMAL,
                            ),
                        )
                    }
                }
            }
        }
        if (width <= 0 || height <= 0) throw IOException("Krita document without a size")
        // Krita lists layers top first; Fern's lists are bottom first.
        return ImportedDocument(width, height, 8, layers.reversed())
    }

    /** A layer's tiles drawn into a [width] x [height] canvas at offset ([dx], [dy]) as 16-bit RGBA. */
    private fun readTiles(file: ByteArray, width: Int, height: Int, dx: Int, dy: Int): ShortArray? {
        if (width.toLong() * height > 30_000L * 30_000L) return null
        val rgba = ShortArray(width * height * 4)
        var at = 0
        fun line(): String {
            val start = at
            while (at < file.size && file[at] != '\n'.code.toByte()) at++
            return String(file, start, at - start).also { at++ }
        }
        var tileW = TILE
        var tileH = TILE
        var pixelSize = 4
        var count = 0
        while (at < file.size) {
            val header = line()
            when {
                header.startsWith("TILEWIDTH") -> tileW = header.substringAfter(' ').trim().toInt()
                header.startsWith("TILEHEIGHT") -> tileH = header.substringAfter(' ').trim().toInt()
                header.startsWith("PIXELSIZE") -> pixelSize = header.substringAfter(' ').trim().toInt()
                header.startsWith("DATA") -> {
                    count = header.substringAfter(' ').trim().toInt()
                    break
                }
            }
        }
        if (pixelSize != 4) return null // 8-bit RGBA only
        repeat(count) {
            val parts = line().split(',')
            if (parts.size < 4) return rgba
            val tx = parts[0].toInt()
            val ty = parts[1].toInt()
            val size = parts[3].toInt()
            val stored = file.copyOfRange(at, at + size)
            at += size
            val plane = tileW * tileH
            val data = if (stored[0].toInt() == 1) lzfDecompress(stored, 1, plane * 4) else stored.copyOfRange(1, stored.size)
            for (y in 0 until tileH) for (x in 0 until tileW) {
                val px = tx + x + dx
                val py = ty + y + dy
                if (px !in 0 until width || py !in 0 until height) continue
                val i = y * tileW + x
                val o = (py * width + px) * 4
                rgba[o] = ((data[2 * plane + i].toInt() and 0xFF) * 257).toShort()
                rgba[o + 1] = ((data[plane + i].toInt() and 0xFF) * 257).toShort()
                rgba[o + 2] = ((data[i].toInt() and 0xFF) * 257).toShort()
                rgba[o + 3] = ((data[3 * plane + i].toInt() and 0xFF) * 257).toShort()
            }
        }
        return rgba
    }

    /** LZF (liblzf) decompression of [input] from [offset], to at most [outputSize] bytes. */
    internal fun lzfDecompress(input: ByteArray, offset: Int, outputSize: Int): ByteArray {
        val out = ByteArray(outputSize)
        var i = offset
        var o = 0
        while (i < input.size && o < outputSize) {
            val ctrl = input[i++].toInt() and 0xFF
            if (ctrl < 32) {
                repeat(ctrl + 1) { if (o < outputSize && i < input.size) out[o++] = input[i++] }
            } else {
                var length = ctrl shr 5
                var ref = o - ((ctrl and 0x1F) shl 8) - 1
                if (length == 7) length += input[i++].toInt() and 0xFF
                ref -= input[i++].toInt() and 0xFF
                if (ref < 0) throw IOException("Corrupt LZF data")
                repeat(length + 2) { if (o < outputSize) out[o++] = out[ref++] }
            }
        }
        return out
    }
}

/**
 * Procreate (.procreate, roadmap item 95): its layers are kept in an undocumented archive, so
 * only the flattened picture it embeds (`QuickLook/Thumbnail.png`) is read, as one layer.
 * Procreate files can't be written.
 */
object ProcreateFormat {
    fun read(bytes: ByteArray): ImportedDocument {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.equals("QuickLook/Thumbnail.png", ignoreCase = true)) {
                    val png = zip.readBytes()
                    val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: break
                    val w = bitmap.width
                    val h = bitmap.height
                    val argb = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
                    val rgba = ShortArray(w * h * 4)
                    for (i in argb.indices) {
                        val c = argb[i]
                        rgba[i * 4] = (((c shr 16) and 0xFF) * 257).toShort()
                        rgba[i * 4 + 1] = (((c shr 8) and 0xFF) * 257).toShort()
                        rgba[i * 4 + 2] = ((c and 0xFF) * 257).toShort()
                        rgba[i * 4 + 3] = ((c ushr 24) * 257).toShort()
                    }
                    return ImportedDocument(w, h, 8, listOf(ImportedLayer("Procreate", 0, 0, w, h, rgba)))
                }
            }
        }
        throw IOException("No picture in the Procreate file")
    }
}

/** Reads a PSD, Krita or Procreate file, recognised by its contents (roadmap item 95). */
fun readLayeredFile(bytes: ByteArray): ImportedDocument {
    if (bytes.size >= 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "8BPS") return PsdFormat.read(bytes)
    val names = HashSet<String>()
    ZipInputStream(bytes.inputStream()).use { zip -> while (true) names.add((zip.nextEntry ?: break).name) }
    return when {
        "maindoc.xml" in names -> KraFormat.read(bytes)
        names.any { it.equals("QuickLook/Thumbnail.png", ignoreCase = true) } -> ProcreateFormat.read(bytes)
        else -> throw IOException("Not a PSD, Krita or Procreate file")
    }
}
