package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

/** The colour conversion feeding the MP4 encoder (the codec itself needs a device). */
class Mp4EncoderTest {
    private fun convert(argb: IntArray, w: Int, h: Int): Triple<IntArray, IntArray, IntArray> {
        val y = IntArray(w * h)
        val u = IntArray(w * h / 4)
        val v = IntArray(w * h / 4)
        argbToYuv420(argb, w, h) { plane, x, row, value ->
            val n = value.toInt() and 0xFF
            when (plane) { 0 -> y[row * w + x] = n; 1 -> u[row * (w / 2) + x] = n; else -> v[row * (w / 2) + x] = n }
        }
        return Triple(y, u, v)
    }

    @Test
    fun whiteAndBlackMapToVideoRangeGreyscale() {
        val (yWhite, uWhite, vWhite) = convert(IntArray(4) { 0xFFFFFFFF.toInt() }, 2, 2)
        assertEquals(235, yWhite[0])
        assertEquals(128, uWhite[0])
        assertEquals(128, vWhite[0])
        val (yBlack, _, _) = convert(IntArray(4) { 0xFF000000.toInt() }, 2, 2)
        assertEquals(16, yBlack[0])
    }

    @Test
    fun redHasHighV() {
        val (_, u, v) = convert(IntArray(4) { 0xFFFF0000.toInt() }, 2, 2)
        assertEquals(240, v[0])
        assertEquals(90, u[0])
    }
}
