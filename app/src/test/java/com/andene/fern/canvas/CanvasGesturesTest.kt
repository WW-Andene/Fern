package com.andene.fern.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D15: canvas gestures run their configured actions. */
@RunWith(RobolectricTestRunner::class)
class CanvasGesturesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aHeldFingerPicksAColourWithoutDrawing() {
        val state = CanvasState()
        val before = state.activeColor
        compose.setContent { DrawingCanvas(state, Modifier.fillMaxSize()) }
        compose.onRoot().performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        compose.waitForIdle()
        assertTrue(state.strokes.isEmpty())
        assertNotEquals(before, state.activeColor)
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
