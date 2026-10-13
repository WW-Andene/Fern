package com.andene.fern.canvas

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Roadmap item 168: the canvas's gestures when real hands are messy - fingers landing and lifting
 * at different times, a pan turning into a pinch, a stylus with a palm resting beside it, and a
 * held finger against a slow stroke.
 */
// API 29: from API 30 the motion-prediction library asks the view's context for its display,
// which Robolectric's activity context doesn't provide (see CanvasGesturesTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GestureTimingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun canvas(): CanvasState {
        val state = CanvasState()
        compose.setContent { DrawingCanvas(state, Modifier.fillMaxSize()) }
        compose.waitForIdle()
        return state
    }

    @Test
    fun fingersLandingAndLiftingAtDifferentTimesAreStillATap() {
        val state = canvas()
        compose.onRoot().performTouchInput { swipe(centerLeft, centerRight) }
        compose.waitForIdle()
        assertEquals(1, state.strokes.size)
        // The second finger lands 40 ms after the first and lifts 40 ms after it: one two-finger tap,
        // which undoes the stroke and leaves no ink of its own (not even from the finger left alone
        // for a moment at the end).
        compose.onRoot().performTouchInput {
            down(0, center)
            advanceEventTime(40)
            down(1, center + Offset(96f, 0f))
            advanceEventTime(40)
            up(0)
            advanceEventTime(40)
            up(1)
        }
        compose.waitForIdle()
        assertEquals(0, state.strokes.size)
    }

    @Test
    fun aPanThatBecomesAPinchMovesThenZoomsWithoutInk() {
        val state = canvas()
        val panBefore = state.panWorld
        // Robolectric's default screen is 320 x 470 px; every point stays on it.
        compose.onRoot().performTouchInput {
            down(0, center - Offset(32f, 0f))
            down(1, center + Offset(32f, 0f))
            // Both fingers sideways together, 32 px: a pan.
            for (step in 1..8) {
                advanceEventTime(16)
                updatePointerTo(0, center + Offset(-32f + step * 4f, 0f))
                updatePointerTo(1, center + Offset(32f + step * 4f, 0f))
                move()
            }
            // Then apart from there, 32 px each way, without lifting: a pinch.
            for (step in 1..8) {
                advanceEventTime(16)
                updatePointerTo(0, center + Offset(0f - step * 4f, 0f))
                updatePointerTo(1, center + Offset(64f + step * 4f, 0f))
                move()
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
        assertEquals(0, state.strokes.size)
        // The content followed the fingers to the right, so the view's centre moved left in the world.
        assertTrue("pan ${state.panWorld} from $panBefore", state.panWorld.x < panBefore.x)
        assertTrue("scale ${state.scale}", state.scale > 1.0)
    }

    @Test
    fun aStylusDrawsWhileARestingPalmIsIgnored() {
        val state = canvas()
        val panBefore = state.panWorld
        val view = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val down = SystemClock.uptimeMillis()
        var time = down
        val pen = 0
        val palm = 1
        fun send(action: Int, pointers: List<Triple<Int, Int, Offset>>) {
            val properties = pointers.map { (id, tool, _) -> MotionEvent.PointerProperties().apply { this.id = id; toolType = tool } }.toTypedArray()
            val coords = pointers.map { (_, _, at) -> MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f } }.toTypedArray()
            val event = MotionEvent.obtain(down, time, action, pointers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_STYLUS or android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            compose.runOnUiThread { view.dispatchTouchEvent(event) }
            event.recycle()
            time += 16
        }
        // On the canvas (Robolectric's default screen is 320 x 470 px): a palm off it would just miss.
        fun penAt(x: Float) = Triple(pen, MotionEvent.TOOL_TYPE_STYLUS, Offset(x, 100f))
        fun palmAt(y: Float) = Triple(palm, MotionEvent.TOOL_TYPE_FINGER, Offset(260f, y))
        send(MotionEvent.ACTION_DOWN, listOf(penAt(40f)))
        send(MotionEvent.ACTION_MOVE, listOf(penAt(56f)))
        // The palm lands below the pen (pointer index 1) and shifts a little as the hand moves.
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(penAt(72f), palmAt(300f)))
        for (step in 1..8) send(MotionEvent.ACTION_MOVE, listOf(penAt(72f + step * 16f), palmAt(300f + step * 8f)))
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(penAt(200f), palmAt(364f)))
        send(MotionEvent.ACTION_UP, listOf(penAt(200f)))
        compose.waitForIdle()
        // One stroke from the pen; the palm neither drew nor turned the stroke into a pan or pinch.
        assertEquals(1, state.strokes.size)
        assertEquals(1.0, state.scale, 1e-9)
        assertEquals(panBefore, state.panWorld)
    }

    @Test
    fun aHeldFingerIsNotAStrokeButASlowStrokeIs() {
        val state = canvas()
        state.screenSampler = null
        // Held still past the hold time: the long press (the eyedropper), no ink.
        compose.onRoot().performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        compose.waitForIdle()
        assertEquals(0, state.strokes.size)
        // A slow stroke: it leaves the touch slop within the hold time, then crawls on, each step
        // well under the hold time apart. It stays a stroke however long it takes.
        compose.onRoot().performTouchInput {
            down(center)
            advanceEventTime(200)
            moveBy(Offset(32f, 0f))
            for (step in 1..10) {
                advanceEventTime(200)
                moveBy(Offset(8f, 4f))
            }
            up()
        }
        compose.waitForIdle()
        assertEquals(1, state.strokes.size)
    }
}
