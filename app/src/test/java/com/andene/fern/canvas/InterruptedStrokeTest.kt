package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D88: a stroke cut short by a call or an app switch is kept exactly as drawn. */
@RunWith(RobolectricTestRunner::class)
class InterruptedStrokeTest {
    @Test
    fun aStrokeInterruptedMidPauseIsKeptNotReadAsAPress() {
        val state = CanvasState()
        state.beginStroke(WorldPoint(0.0, 0.0))
        state.extendStroke(WorldPoint(1.0, 0.0)) // still within the hold jitter: a lift now would be an eyedropper press
        // The call comes in while the finger rests: longer than the hold time.
        Thread.sleep(HOLD_PAST_MILLIS)
        state.interruptStroke()
        assertEquals(1, state.strokes.size)
    }

    private companion object {
        // Past the hold time the pen reads as a press (SHAPE_HOLD_MILLIS).
        const val HOLD_PAST_MILLIS = 700L
    }
}
