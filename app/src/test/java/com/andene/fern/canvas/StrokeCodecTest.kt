package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class StrokeCodecTest {
    private fun encode(strokes: List<Stroke>): ByteArray =
        ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), strokes) }.toByteArray()

    private fun decode(bytes: ByteArray): List<Stroke> = StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes)))

    @Test
    fun roundTripsEveryField() {
        val stylus = Stroke(Color(0x80FF0000.toInt()), 3.5, PenType.CALLIGRAPHY, blendMode = StrokeBlendMode.MULTIPLY, layerId = "ink", animFrame = 4, dashed = true).apply {
            addPoint(WorldPoint(1.0 / 3.0, -1e9), 0.25f, 0.5f, 1.25f)
            addPoint(WorldPoint(2.0, 3.0), 0.75f, 0f, 0f)
        }
        val fill = Stroke(Color.Blue, 1.0, filled = true, fadeFill = true, screentone = true).apply {
            addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(5.0, 0.0)); addPoint(WorldPoint(0.0, 5.0))
        }
        val decoded = decode(encode(listOf(stylus, fill)))
        assertEquals(2, decoded.size)
        for ((a, b) in listOf(stylus, fill).zip(decoded)) {
            assertEquals(a.color, b.color)
            assertEquals(a.widthWorld, b.widthWorld, 0.0)
            assertEquals(a.penType, b.penType)
            assertEquals(a.blendMode, b.blendMode)
            assertEquals(a.layerId, b.layerId)
            assertEquals(a.animFrame, b.animFrame)
            assertEquals(a.filled, b.filled)
            assertEquals(a.dashed, b.dashed)
            assertEquals(a.fadeFill, b.fadeFill)
            assertEquals(a.screentone, b.screentone)
            assertEquals(a.points, b.points)
            assertEquals(a.pressures, b.pressures)
            assertEquals(a.tilts, b.tilts)
            assertEquals(a.orientations, b.orientations)
        }
    }

    @Test
    fun fingerStrokesSpendSixteenBytesPerPoint() {
        fun sized(points: Int) = encode(listOf(Stroke(Color.Black, 2.0).apply { repeat(points) { addPoint(WorldPoint(it.toDouble(), 0.0)) } })).size
        assertEquals(16 * 100, sized(200) - sized(100))
    }

    @Test(expected = IOException::class)
    fun rejectsNewerVersion() {
        val bytes = encode(emptyList())
        bytes[7] = 99 // low byte of the version int
        decode(bytes)
    }

    @Test(expected = IOException::class)
    fun rejectsForeignData() {
        decode("[{\"x\":1}]".toByteArray())
    }

    @Test
    fun emptyListRoundTrips() {
        assertTrue(decode(encode(emptyList())).isEmpty())
    }
}
