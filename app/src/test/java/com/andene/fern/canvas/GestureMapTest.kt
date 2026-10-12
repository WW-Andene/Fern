package com.andene.fern.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D15: each gesture has a default action, and a changed one is kept. */
@RunWith(RobolectricTestRunner::class)
class GestureMapTest {
    @Test
    fun defaultsThenAChangePersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val defaults = GestureSettings.read(context)
        assertEquals(GestureAction.UNDO, defaults[CanvasGesture.TWO_FINGER_TAP])
        assertEquals(GestureAction.EYEDROPPER, defaults[CanvasGesture.LONG_PRESS])
        GestureSettings.set(context, CanvasGesture.FOUR_FINGER_TAP, GestureAction.COMMAND_PALETTE)
        assertEquals(GestureAction.COMMAND_PALETTE, GestureSettings.read(context)[CanvasGesture.FOUR_FINGER_TAP])
        assertEquals(GestureAction.REDO, GestureSettings.read(context)[CanvasGesture.THREE_FINGER_TAP])
    }
}
