package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class StrokeChunkFileTest {
    // Two regions far apart, each filling two chunks, interleaved in drawing order by region.
    private val strokes = List(StrokeChunkFile.CHUNK_STROKES * 4) { i ->
        val x = if (i / (StrokeChunkFile.CHUNK_STROKES * 2) == 0) i.toDouble() % 100 else 1e6 + i % 100
        Stroke(Color.Black, 1.0).apply { addPoint(WorldPoint(x, 0.0)); addPoint(WorldPoint(x + 1, 1.0)) }
    }
    private val bytes = ByteArrayOutputStream().also { StrokeChunkFile.write(DataOutputStream(it), strokes) }.toByteArray()

    private fun open() = DataInputStream(ByteArrayInputStream(bytes))

    @Test
    fun everythingComesBackInOrder() {
        val read = StrokeChunkFile.read(open())
        assertEquals(strokes.map { it.points.first() }, read.map { it.points.first() })
    }

    @Test
    fun theAreaInViewIsReadFirstThenTheRest() {
        var first: List<Stroke>? = null
        val read = StrokeChunkFile.read(open(), StrokeChunkFile.Area(1e6 - 10, -10.0, 1e6 + 200, 10.0), { first = it }, ::open)
        assertEquals(StrokeChunkFile.CHUNK_STROKES * 2, first!!.size)
        assertEquals(strokes.drop(StrokeChunkFile.CHUNK_STROKES * 2).map { it.points.first() }, first!!.map { it.points.first() })
        assertEquals(strokes.map { it.points.first() }, read.map { it.points.first() })
    }

    @Test
    fun aSingleBlockFileFromBeforeChunksStillReads() {
        val legacy = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), strokes.take(3)) }.toByteArray()
        assertEquals(3, StrokeChunkFile.read(DataInputStream(ByteArrayInputStream(legacy))).size)
    }
}
