package com.andene.fern.canvas

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Deflater

/*
 * The small PDF writer shared by the CMYK print PDF (roadmap item 102) and the AI export
 * (roadmap item 103): numbered objects, a cross-reference table, Flate-compressed streams.
 */

/** A minimal PDF file builder: numbered objects, then a cross-reference table and trailer. */
internal class PdfWriter {
    private val objects = ArrayList<ByteArray?>()

    fun reserve(): Int {
        objects.add(null)
        return objects.size
    }

    fun set(number: Int, body: String) {
        objects[number - 1] = body.toByteArray(Charsets.ISO_8859_1)
    }

    fun add(body: String): Int = add(body.toByteArray(Charsets.ISO_8859_1))

    fun add(body: ByteArray): Int {
        objects.add(body)
        return objects.size
    }

    fun write(root: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun put(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        put("%PDF-1.4\n%âãÏÓ\n") // a binary comment marks the file as binary
        val offsets = IntArray(objects.size)
        for ((i, body) in objects.withIndex()) {
            offsets[i] = out.size()
            put("${i + 1} 0 obj\n")
            out.write(body ?: error("PDF object ${i + 1} was never written"))
            put("\nendobj\n")
        }
        val xref = out.size()
        put("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        for (offset in offsets) put(String.format(Locale.ROOT, "%010d 00000 n \n", offset))
        put("trailer\n<< /Size ${objects.size + 1} /Root $root 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}

internal fun deflate(bytes: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    deflater.setInput(bytes)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(65536)
    while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
    deflater.end()
    return out.toByteArray()
}

/** [value] as a PDF number: up to three decimals, no trailing zeros, never "-0". */
internal fun pdfNumber(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.').let { if (it.isEmpty() || it == "-0") "0" else it }
