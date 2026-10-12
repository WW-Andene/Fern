package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer

/** Roadmap item 102: reading a CMYK ICC profile and converting colours through it. */
class IccProfileTest {
    /** An mft2 table: identity curves (2 entries), a 2-point grid CLUT filled by [clut]. */
    private fun mft2(inputs: Int, outputs: Int, clut: (IntArray) -> FloatArray): ByteArray {
        val points = 1 shl inputs
        val buf = ByteBuffer.allocate(52 + inputs * 2 * 2 + points * outputs * 2 + outputs * 2 * 2)
        buf.put("mft2".toByteArray()).putInt(0).put(inputs.toByte()).put(outputs.toByte()).put(2).put(0)
        repeat(9) { buf.putInt(if (it % 4 == 0) 65536 else 0) } // identity matrix
        buf.putShort(2).putShort(2)
        repeat(inputs) { buf.putShort(0).putShort(-1) } // 0 and 0xFFFF
        for (corner in 0 until points) {
            // First input varies slowest.
            val index = IntArray(inputs) { d -> (corner shr (inputs - 1 - d)) and 1 }
            for (v in clut(index)) buf.putShort((v * 65535 + 0.5f).toInt().toShort())
        }
        repeat(outputs) { buf.putShort(0).putShort(-1) }
        return buf.array()
    }

    private fun profile(): ByteArray {
        // B2A0: C = 1 - L, no other ink. A2B0: L = 1 - C, neutral a and b.
        val b2a = mft2(3, 4) { (l, _, _) -> floatArrayOf(1f - l, 0f, 0f, 0f) }
        val a2b = mft2(4, 3) { (c, _, _, _) -> floatArrayOf(1f - c, 0.5f, 0.5f) }
        val tagsAt = 128 + 4 + 2 * 12
        val size = tagsAt + b2a.size + a2b.size
        val buf = ByteBuffer.allocate(size)
        buf.putInt(size).put("lcms".toByteArray()).putInt(0x02100000).put("prtr".toByteArray()).put("CMYK".toByteArray()).put("Lab ".toByteArray())
        buf.position(36); buf.put("acsp".toByteArray())
        buf.position(128); buf.putInt(2)
        buf.put("B2A0".toByteArray()).putInt(tagsAt).putInt(b2a.size)
        buf.put("A2B0".toByteArray()).putInt(tagsAt + b2a.size).putInt(a2b.size)
        buf.put(b2a).put(a2b)
        return buf.array()
    }

    @Test
    fun coloursConvertThroughTheProfilesTables() {
        val icc = IccProfile.read(profile())
        val white = icc.toCmyk(floatArrayOf(1f, 1f, 1f))
        assertEquals(0f, white[0], 0.01f)
        val black = icc.toCmyk(floatArrayOf(0f, 0f, 0f))
        assertEquals(1f, black[0], 0.01f)
        val grey = icc.toCmyk(floatArrayOf(0.5f, 0.5f, 0.5f))
        assertEquals(1f - 53.4f / 100f, grey[0], 0.02f) // sRGB mid grey is L* 53.4
        val paper = icc.toRgb(floatArrayOf(0f, 0f, 0f, 0f))
        assertTrue(paper.all { it > 0.97f })
        val solid = icc.toRgb(floatArrayOf(1f, 0f, 0f, 0f))
        assertTrue(solid.all { it < 0.03f })
    }

    @Test
    fun labAndXyzRoundTripAndOtherFilesAreRefused() {
        val lab = xyzToLab(srgbToXyzD50(floatArrayOf(1f, 0f, 0f)))
        assertEquals(54.3, lab[0], 0.5) // sRGB red, D50
        val back = xyzD50ToSrgb(labToXyz(lab))
        assertEquals(1f, back[0], 0.01f)
        assertEquals(0f, back[1], 0.01f)
        try {
            IccProfile.read(ByteArray(200))
            throw AssertionError("read a non-profile")
        } catch (e: IOException) {
            // expected
        }
    }
}
