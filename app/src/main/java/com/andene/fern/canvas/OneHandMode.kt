package com.andene.fern.canvas

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

private const val KEY_ONE_HAND = "one_hand"

/**
 * One-hand mode (design roadmap D55), for a phone held and used in the same hand: everything
 * within the thumb's reach in the bottom third of the screen. With it on and the rail along the
 * bottom, the toolbar moves to the bottom, the controls that otherwise sit high up (navigation,
 * modes, the quick menu button, messages, progress, the animation and audio bars) stack just above
 * it with the quick menu button on the thumb's side, and sheets open no higher than a third of
 * the screen. Off by default; it has no effect with the side rail (a phone on its side, a tablet).
 */
object OneHandMode {
    private var loaded: MutableState<Boolean>? = null

    fun state(context: Context): MutableState<Boolean> =
        loaded ?: mutableStateOf(AppSettings.prefs(context).getBoolean(KEY_ONE_HAND, false)).also { loaded = it }

    fun set(context: Context, on: Boolean) {
        AppSettings.prefs(context).edit().putBoolean(KEY_ONE_HAND, on).apply()
        state(context).value = on
    }
}

/** Whether one-hand mode is on, following a change made in Settings. */
@Composable
fun oneHandModeOn(): Boolean {
    val on by OneHandMode.state(LocalContext.current)
    return on
}
