package com.andene.fern.canvas

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.DataInputStream
import java.util.zip.CRC32

/** Roadmap item 80: animated PNG export is a well-formed APNG whose first frame is a plain PNG. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApngEncoderTest {
    private val colours = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0x800000FF.toInt())
    private fun frames() = colours.map { c -> IntArray(8 * 6) { c } }

    @Test
    fun chunksAreValidAndSequenced() {
        val bytes = encodeApng(frames(), 8, 6, fps = 12)
        val input = DataInputStream(bytes.inputStream())
        input.skipBytes(8)
        val types = ArrayList<String>()
        val sequence = ArrayList<Int>()
        var frameCount = -1
        while (input.available() > 0) {
            val length = input.readInt()
            val typeBytes = ByteArray(4).also { input.readFully(it) }
            val body = ByteArray(length).also { input.readFully(it) }
            assertEquals(CRC32().apply { update(typeBytes); update(body) }.value.toInt(), input.readInt())
            val type = String(typeBytes)
            types.add(type)
            val head = DataInputStream(body.inputStream())
            when (type) {
                "acTL" -> frameCount = head.readInt()
                "fcTL", "fdAT" -> sequence.add(head.readInt())
            }
        }
        assertEquals(3, frameCount)
        assertEquals("IHDR", types.first())
        assertEquals("acTL", types[1])
        assertEquals("IEND", types.last())
        assertEquals(3, types.count { it == "fcTL" })
        assertEquals(sequence.indices.toList(), sequence) // 0, 1, 2, ... with no gaps
        assertEquals(types.indexOf("fcTL") + 1, types.indexOf("IDAT")) // the first frame is the default image
    }

    @Test
    fun firstFrameDecodesAsAPlainPng() {
        val bytes = encodeApng(frames(), 8, 6, fps = 12)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(8, bitmap.width)
        assertEquals(0xFFFF0000.toInt(), bitmap.getPixel(3, 3))
    }
}
