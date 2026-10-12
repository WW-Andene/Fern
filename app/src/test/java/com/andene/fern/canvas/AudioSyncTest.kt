package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Roadmap item 130: ink remembers when it was drawn, so a voice note replays in step with it. */
class AudioSyncTest {
    private fun line(x: Double, at: Long) = Stroke(Color.Black, 2.0).apply {
        addPoint(WorldPoint(x, 0.0)); addPoint(WorldPoint(x, 100.0))
        setDrawnAt(at)
    }

    @Test
    fun drawnAtIsSavedCopiedAndSetWhenDrawing() {
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), listOf(line(0.0, 1_700_000_000_123L), line(5.0, 0L))) }.toByteArray()
        val read = StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(listOf(1_700_000_000_123L, 0L), read.map { it.drawnAt })
        assertEquals(42L, line(0.0, 42L).deepCopy().drawnAt)
        assertEquals(42L, line(0.0, 42L).withColor(Color.Red).drawnAt)

        val state = CanvasState()
        val before = System.currentTimeMillis()
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(50.0, 0.0)); state.endStroke()
        assertTrue(state.strokes.single().drawnAt >= before)
    }

    @Test
    fun playbackFadesInkItHasntReachedAndFindsWhereInkWasDrawn() {
        val replay = AudioReplay(startMillis = 1000L, endMillis = 5000L, positionMillis = 2000L)
        val early = line(0.0, 2500L)
        val late = line(10.0, 4000L)
        val before = line(60.0, 500L)
        assertFalse(replay.isAhead(early))
        assertTrue(replay.isAhead(late))
        assertFalse(replay.isAhead(before)) // not part of this recording
        assertEquals(3000L, replay.positionOf(late))
        assertNull(replay.positionOf(before))
        assertEquals(1234L, recordingStart(File("doc_1234.m4a")))

        val state = CanvasState()
        state.loadStrokes(listOf(early, late, before))
        state.audioReplay = replay
        assertSame(state.strokes[1], state.syncedStrokeAt(WorldPoint(11.0, 50.0)))
        assertNull(state.syncedStrokeAt(WorldPoint(60.0, 50.0))) // drawn before the recording
    }
}
