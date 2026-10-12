package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class TipMaskTest {
    @Test
    fun darkPixelsOfAnOpaquePictureBecomeInk() {
        // Left half black, right half white.
        val pixels = IntArray(4 * 4) { if (it % 4 < 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val mask = tipMaskFromArgb(pixels, 4, 4, size = 2)
        assertEquals(255, mask.alpha[0].toInt() and 0xFF)
        assertEquals(0, mask.alpha[1].toInt() and 0xFF)
    }

    @Test
    fun transparencyIsUsedWhenPresentAndPicturesAreNotStretched() {
        // A 2x1 fully opaque-alpha red bar with one transparent pixel, fitted into a square.
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0x00FF0000)
        val mask = tipMaskFromArgb(pixels, 2, 1, size = 2)
        // Top row is padding above the bar's middle; rows split the 2-high square evenly.
        assertTrue((mask.alpha[0].toInt() and 0xFF) in 100..155)
        assertEquals(0, mask.alpha[1].toInt() and 0xFF)
    }

    @Test
    fun imageTipSurvivesTheStrokeFormat() {
        val mask = TipMask(2, byteArrayOf(0, 64, -128, -1))
        val stroke = Stroke(Color.Black, 8.0, PenType.STAMP, brush = StampBrush(tip = StampTip.IMAGE, mask = mask))
        stroke.addPoint(WorldPoint(0.0, 0.0))
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), listOf(stroke)) }.toByteArray()
        val read = StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes))).single()
        assertEquals(StampTip.IMAGE, read.brush?.tip)
        assertEquals(mask, read.brush?.mask)
    }
}
