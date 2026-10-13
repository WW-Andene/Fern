package com.andene.fern.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Design roadmap D15: canvas gestures run their configured actions. */
// API 29: from API 30 the motion-prediction library asks the view's context for its display,
// which Robolectric's activity context doesn't provide (see MainActivityLoadingTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CanvasGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aHeldFingerPicksAColourWithoutDrawing() {
        val state = CanvasState()
        compose.setContent { DrawingCanvas(state, Modifier.fillMaxSize()) }
        // Ink to pick from: the eyedropper leaves the colour alone over an empty canvas.
        state.activeColor = Color.Red
        compose.onRoot().performTouchInput { swipe(centerLeft, centerRight) }
        compose.waitForIdle()
        state.activeColor = Color.Blue
        compose.onRoot().performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, state.strokes.size)
        assertEquals(Color.Red, state.activeColor)
    }

    @Test
    fun aFourFingerTapGoesToTheScreen() {
        val state = CanvasState()
        val actions = mutableListOf<GestureAction>()
        compose.setContent { DrawingCanvas(state, Modifier.fillMaxSize(), onGestureAction = { actions += it }) }
        compose.onRoot().performTouchInput {
            for (i in 0 until 4) down(i, center + Offset(i * 40f, 0f))
            advanceEventTime(50)
            for (i in 0 until 4) up(i)
        }
        compose.waitForIdle()
        assertEquals(listOf(GestureAction.TOGGLE_CHROME), actions)
    }
}
