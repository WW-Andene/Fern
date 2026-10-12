package com.andene.fern.canvas

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

/**
 * Encodes ARGB [frames] (each [width] x [height]) as a looping animated PNG (APNG, roadmap item
 * 80), each frame shown for its [holds] count (default 1) of 1 / [fps] s ticks. Unlike GIF it keeps full colour and alpha. Each frame
 * is compressed by the platform's PNG encoder, then its image data is moved into APNG frame
 * chunks: the first frame stays IDAT (the still image viewers without APNG support show), the
 * rest become fdAT. Every frame covers the whole image and replaces the previous one.
 */
fun encodeApng(frames: List<IntArray>, width: Int, height: Int, fps: Int, holds: List<Int> = emptyList()): ByteArray {
    require(frames.isNotEmpty())
    val out = ByteArrayOutputStream()
    val data = DataOutputStream(out)
    data.write(PNG_SIGNATURE)
    var sequence = 0
    for ((index, pixels) in frames.withIndex()) {
        val chunks = pngChunks(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))
        if (index == 0) {
            writeChunk(data, "IHDR", chunks.first { it.first == "IHDR" }.second)
            writeChunk(data, "acTL", ints(frames.size, 0)) // 0 plays = loop forever
        }
        val control = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(sequence++)
                writeInt(width)
                writeInt(height)
                writeInt(0) // x offset
                writeInt(0) // y offset
                writeShort((holds.getOrNull(index) ?: 1).coerceIn(1, Short.MAX_VALUE.toInt())) // delay numerator: ticks held
                writeShort(fps.coerceIn(1, Short.MAX_VALUE.toInt())) // delay denominator
                writeByte(0) // dispose: none
                writeByte(0) // blend: source (replace)
            }
        }.toByteArray()
        writeChunk(data, "fcTL", control)
        for ((type, body) in chunks) {
            if (type != "IDAT") continue
            if (index == 0) writeChunk(data, "IDAT", body) else writeChunk(data, "fdAT", ints(sequence++) + body)
        }
    }
    writeChunk(data, "IEND", ByteArray(0))
    return out.toByteArray()
}

/** The (type, data) chunks of [bitmap] encoded as a PNG. */
private fun pngChunks(bitmap: Bitmap): List<Pair<String, ByteArray>> {
    val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    val input = DataInputStream(png.inputStream())
    input.skipBytes(PNG_SIGNATURE.size)
    val chunks = ArrayList<Pair<String, ByteArray>>()
    while (input.available() > 0) {
        val length = input.readInt()
        val type = String(ByteArray(4).also { input.readFully(it) }, Charsets.US_ASCII)
        val body = ByteArray(length).also { input.readFully(it) }
        input.readInt() // CRC, recomputed on write
        chunks.add(type to body)
        if (type == "IEND") break
    }
    return chunks
}

private fun ints(vararg values: Int): ByteArray =
    ByteArrayOutputStream().also { out -> DataOutputStream(out).apply { values.forEach { writeInt(it) } } }.toByteArray()

private fun writeChunk(out: DataOutputStream, type: String, body: ByteArray) {
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    out.writeInt(body.size)
    out.write(typeBytes)
    out.write(body)
    out.writeInt(CRC32().apply { update(typeBytes); update(body) }.value.toInt())
}
