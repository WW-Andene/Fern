package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Roadmap item 139: OpenEXR in and out. */
class OpenExrTest {
    private fun gradient(w: Int, h: Int) = ExrImage(w, h, FloatArray(w * h * 4) { i ->
        val p = i / 4
        when (i % 4) {
            0 -> p % w / w.toFloat() * 4f // HDR: up to four times paper white
            1 -> p / w / h.toFloat()
            2 -> 0.25f
            else -> 1f
        }
    })

    @Test
    fun halfFloatsRoundTrip() {
        for (v in listOf(0f, 1f, -2f, 0.5f, 65504f, 1e-5f, 3.14159f)) {
            val back = halfToFloat(floatToHalf(v))
            assertEquals(v, back, maxOf(abs(v) * 1e-3f, 1e-7f))
        }
        assertEquals(0x3C00.toShort(), floatToHalf(1f))
        assertTrue(halfToFloat(floatToHalf(1e6f)).isInfinite())
    }

    @Test
    fun everyCompressionRoundTripsWithinHalfPrecision() {
        val image = gradient(37, 21) // not a multiple of the 16-line ZIP block
        for (compression in listOf(OpenExr.NO_COMPRESSION, OpenExr.ZIPS_COMPRESSION, OpenExr.ZIP_COMPRESSION)) {
            val read = OpenExr.read(OpenExr.write(image, compression))
            assertEquals(37, read.width)
            assertEquals(21, read.height)
            for (i in image.rgba.indices) assertEquals("compression $compression at $i", image.rgba[i], read.rgba[i], maxOf(abs(image.rgba[i]) * 1e-3f, 1e-4f))
        }
        assertTrue(OpenExr.write(image, OpenExr.ZIP_COMPRESSION).size < OpenExr.write(image, OpenExr.NO_COMPRESSION).size)
    }

    @Test
    fun aFloatGreyFileReadsAsGrey() {
        // Hand-built: one FLOAT channel "Y", 2 x 1 pixels, no compression.
        val out = ByteArrayOutputStream()
        fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
        fun attr(name: String, type: String, value: ByteArray) {
            out.write(name.toByteArray()); out.write(0); out.write(type.toByteArray()); out.write(0)
            out.write(le(4).putInt(value.size).array()); out.write(value)
        }
        out.write(le(8).putInt(20000630).putInt(2).array())
        attr("channels", "chlist", byteArrayOf('Y'.code.toByte(), 0) + le(16).putInt(2).putInt(0).putInt(1).putInt(1).array() + byteArrayOf(0))
        attr("compression", "compression", byteArrayOf(0))
        attr("dataWindow", "box2i", le(16).putInt(0).putInt(0).putInt(1).putInt(0).array())
        out.write(0)
        val tableAt = out.size()
        out.write(le(8).putLong(tableAt + 8L).array())
        out.write(le(16).putInt(0).putInt(8).putFloat(0.5f).putFloat(2f).array())
        val read = OpenExr.read(out.toByteArray())
        assertEquals(0.5f, read.rgba[0], 0f)
        assertEquals(2f, read.rgba[5], 0f)
        assertEquals(1f, read.rgba[3], 0f) // no alpha channel: opaque
    }

    @Test
    fun importTurnsLinearLightIntoSrgbAndExportBack() {
        val doc = OpenExr.read(OpenExr.write(ExrImage(1, 1, floatArrayOf(0.214f, 0.214f, 0.214f, 1f)))).toImportedDocument("x")
        val v = (doc.layers.single().rgba16[0].toInt() and 0xFFFF) / 65535f
        assertEquals(0.5f, v, 0.01f) // linear 0.214 is sRGB mid grey
        val back = exrImageFromArgb(intArrayOf(0xFF808080.toInt()), 1, 1)
        assertEquals(0.216f, back.rgba[0], 0.01f)
        assertEquals(1f, back.rgba[3], 0f)
    }

    private fun abs(v: Float) = kotlin.math.abs(v)
}
