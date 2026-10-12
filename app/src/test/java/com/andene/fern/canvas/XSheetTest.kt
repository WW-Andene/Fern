package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 88: frame holds in playback, frame edits, undo, storage and exports. */
@RunWith(RobolectricTestRunner::class)
class XSheetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun threeFrames() = CanvasState().apply {
        loadStrokes(List(3) { f -> Stroke(Color.Black, 2.0, animFrame = f).apply { addPoint(WorldPoint(0.0, f * 10.0)); addPoint(WorldPoint(50.0, f * 10.0)) } })
    }

    @Test
    fun playbackHoldsAFrameForItsTicks() {
        val state = threeFrames()
        state.setHold(0, 3)
        val shown = (0 until 6).map { state.currentAnimFrame.also { state.advanceAnimFrame() } }
        assertEquals(listOf(0, 0, 0, 1, 2, 0), shown)
    }

    @Test
    fun holdsMoveWithInsertedAndDeletedFramesAndUndo() {
        val state = threeFrames()
        state.setHold(1, 2)
        state.goToAnimFrame(0)
        state.insertAnimFrame(copyCurrent = false)
        assertEquals(mapOf(2 to 2), state.frameHolds.toMap())
        state.undo()
        assertEquals(mapOf(1 to 2), state.frameHolds.toMap())
        state.goToAnimFrame(1)
        state.deleteAnimFrame()
        assertEquals(emptyMap<Int, Int>(), state.frameHolds.toMap())
    }

    @Test
    fun timingIsSavedAndExportsFollowIt() {
        val doc = CanvasStorage.createDocument(context, "Walk")
        CanvasStorage.saveAnimationSettings(context, doc.id, AnimationSettings(8, mapOf(2 to 4)))
        assertEquals(AnimationSettings(8, mapOf(2 to 4)), CanvasStorage.loadAnimationSettings(context, doc.id))
        val state = threeFrames()
        state.loadAnimationSettings(AnimationSettings(8, mapOf(2 to 4)))
        val lottie = JSONObject(exportLottie(state)!!)
        assertEquals(6, lottie.getInt("op")) // 1 + 1 + 4 ticks
        assertEquals(2, lottie.getJSONArray("layers").getJSONObject(2).getInt("ip"))
    }

    @Test
    fun mouthChartMovesWithFramesAndIsSavedWithTheSoundtrack() {
        val state = threeFrames()
        state.setMouth(1, MouthShape.O)
        state.goToAnimFrame(0)
        state.insertAnimFrame(copyCurrent = false)
        assertEquals(mapOf(2 to MouthShape.O), state.frameMouths.toMap())
        state.goToAnimFrame(2)
        state.deleteAnimFrame()
        assertEquals(emptyMap<Int, MouthShape>(), state.frameMouths.toMap())

        val doc = CanvasStorage.createDocument(context, "Talk")
        val name = CanvasStorage.importSoundtrack(context, ByteArray(16).inputStream(), "wav", replacing = null)!!
        val settings = AnimationSettings(12, emptyMap(), mapOf(0 to MouthShape.MBP, 3 to MouthShape.AI), name)
        CanvasStorage.saveAnimationSettings(context, doc.id, settings)
        assertEquals(settings, CanvasStorage.loadAnimationSettings(context, doc.id))
        CanvasStorage.deleteDocument(context, doc.id)
        assertEquals(false, CanvasStorage.soundtrackFile(context, name).exists())
    }

    @Test
    fun framesStartWhereTheXSheetSays() {
        val state = threeFrames()
        state.loadAnimationSettings(AnimationSettings(10, mapOf(0 to 3)))
        assertEquals(listOf(0L, 300L, 400L, 500L), (0..3).map { state.frameStartMillis(it) })
        assertEquals(1, LipSync.frameAt(listOf(0L, 300L, 400L, 500L), 350))
        assertEquals(0, LipSync.frameAt(listOf(0L, 300L, 400L, 500L), 0))
    }

    @Test
    fun loudnessPicksWiderMouthsAndSilenceRests() {
        val mouths = LipSync.mouthsFor(floatArrayOf(0f, 0.05f, 0.2f, 0.4f, 0.6f, 1f))
        assertEquals(MouthShape.REST, mouths[0])
        assertEquals(MouthShape.REST, mouths[1]) // under a tenth of the loudest
        assertEquals(MouthShape.MBP, mouths[2])
        assertEquals(MouthShape.E, mouths[3])
        assertEquals(MouthShape.AI, mouths[4])
        assertEquals(MouthShape.O, mouths[5])
    }
}
