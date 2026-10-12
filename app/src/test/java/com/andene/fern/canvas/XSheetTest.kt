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
}
