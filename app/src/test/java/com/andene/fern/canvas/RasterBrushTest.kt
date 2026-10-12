package com.andene.fern.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Roadmap item 90: wet mixing, smudge, blur and imported brush tips. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterBrushTest {
    private fun state(color: Color) = CanvasState().apply {
        addRasterLayer(16)
        activeColor = color
        activeWidthWorld = 16.0
    }

    private fun line(state: CanvasState, from: WorldPoint, to: WorldPoint) {
        state.beginStroke(from)
        state.extendStroke(WorldPoint((from.x + to.x) / 2, (from.y + to.y) / 2))
        state.extendStroke(to)
        state.endStroke()
    }

    @Test
    fun wetPaintPicksUpTheColourItCrosses() {
        val state = state(Color.Red)
        val surface = state.rasters.getValue(state.activeLayerId)
        line(state, WorldPoint(40.0, 0.0), WorldPoint(40.0, 80.0)) // a red bar
        state.activeColor = Color.Blue
        state.rasterBrush = RasterBrush(RasterBrushMode.WET, wetness = 0.8f)
        line(state, WorldPoint(10.0, 40.0), WorldPoint(80.0, 40.0)) // blue across it
        val after = surface.pixel(70, 40)
        assertTrue("carried red past the bar", after[0] > 0.1f)
        assertTrue(after[2] > 0.1f)
    }

    @Test
    fun smudgeDragsPaintAlong() {
        val state = state(Color.Black)
        val surface = state.rasters.getValue(state.activeLayerId)
        line(state, WorldPoint(20.0, 0.0), WorldPoint(20.0, 60.0))
        assertEquals(0f, surface.pixel(45, 30)[3], 0f)
        state.rasterBrush = RasterBrush(RasterBrushMode.SMUDGE, strength = 0.9f)
        line(state, WorldPoint(20.0, 30.0), WorldPoint(50.0, 30.0))
        assertTrue(surface.pixel(45, 30)[3] > 0.05f) // ink pulled to the right
    }

    @Test
    fun blurSoftensAHardEdge() {
        val state = state(Color.Black)
        val surface = state.rasters.getValue(state.activeLayerId)
        state.activeWidthWorld = 64.0
        line(state, WorldPoint(0.0, 32.0), WorldPoint(0.0, 33.0)) // a hard disc of radius 32 at x = 0
        val edgeBefore = surface.pixel(34, 32)[3]
        state.rasterBrush = RasterBrush(RasterBrushMode.BLUR, strength = 1f)
        state.activeWidthWorld = 32.0
        repeat(6) { line(state, WorldPoint(31.0, 32.0), WorldPoint(31.5, 32.0)) }
        assertTrue(surface.pixel(34, 32)[3] > edgeBefore) // paint spread past the edge
    }

    @Test
    fun abrSampledBrushesAreRead() {
        // ABR version 6, subversion 1: one 8BIM "samp" section with two 2 x 2 brushes,
        // one raw and one PackBits-compressed.
        fun sample(compressed: Boolean): ByteArray {
            val b = ByteArrayOutputStream()
            DataOutputStream(b).apply {
                write(ByteArray(47))
                writeInt(0); writeInt(0); writeInt(2); writeInt(2) // top, left, bottom, right
                writeShort(8)
                writeByte(if (compressed) 1 else 0)
                if (compressed) {
                    writeShort(2); writeShort(2) // row lengths
                    writeByte(-1); writeByte(255) // row 0: 255 twice
                    writeByte(-1); writeByte(0) // row 1: 0 twice
                } else {
                    write(byteArrayOf(-1, -1, 0, 0))
                }
            }
            return b.toByteArray()
        }
        val samp = ByteArrayOutputStream()
        DataOutputStream(samp).apply {
            for (c in listOf(false, true)) {
                val s = sample(c)
                writeInt(s.size)
                write(s)
                repeat((4 - s.size % 4) % 4) { writeByte(0) }
            }
        }
        val file = ByteArrayOutputStream()
        DataOutputStream(file).apply {
            writeShort(6); writeShort(1)
            write("8BIM".toByteArray()); write("samp".toByteArray())
            writeInt(samp.size()); write(samp.toByteArray())
        }
        val tips = BrushImport.read(file.toByteArray())
        assertEquals(2, tips.size)
        for (tip in tips) {
            assertEquals(255, tip.alpha[0].toInt() and 0xFF) // top row ink
            assertEquals(0, tip.alpha[tip.size * (tip.size - 1)].toInt() and 0xFF) // bottom row none
        }
    }

    @Test
    fun procreateShapesAreWhiteOnBlack() {
        val shape = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLACK)
            for (x in 0 until 4) setPixel(x, 0, android.graphics.Color.WHITE)
        }
        val png = ByteArrayOutputStream().also { shape.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { it.putNextEntry(ZipEntry("Shape.png")); it.write(png); it.closeEntry() }
        val tip = BrushImport.read(zip.toByteArray()).single()
        assertEquals(255, tip.alpha[0].toInt() and 0xFF)
        assertEquals(0, tip.alpha[tip.size * (tip.size - 1)].toInt() and 0xFF)
    }
}
