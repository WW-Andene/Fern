package com.andene.fern

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's haptic vocabulary (design roadmap D7): a light [tick] when something lines up or
 * changes under the hand (a snap, a guide, zoom passing 100 %, a tool switch), and a firmer
 * [press] when a long-press opens a menu. [enabled] follows the Settings switch.
 */
object FernHaptics {
    var enabled by mutableStateOf(true)

    fun tick(view: View) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun press(view: View) {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
