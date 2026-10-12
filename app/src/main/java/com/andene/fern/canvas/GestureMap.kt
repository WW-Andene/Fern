package com.andene.fern.canvas

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import org.json.JSONObject

/** A canvas gesture that can be given an action (design roadmap D15). */
enum class CanvasGesture(@StringRes val label: Int, val default: GestureAction) {
    TWO_FINGER_TAP(R.string.gesture_two_finger_tap, GestureAction.UNDO),
    THREE_FINGER_TAP(R.string.gesture_three_finger_tap, GestureAction.REDO),
    FOUR_FINGER_TAP(R.string.gesture_four_finger_tap, GestureAction.TOGGLE_CHROME),
    /** A finger held still on the canvas. */
    LONG_PRESS(R.string.gesture_long_press, GestureAction.EYEDROPPER),
    /** Three fingers moved together without pinching. */
    THREE_FINGER_SWIPE(R.string.gesture_three_finger_swipe, GestureAction.QUICK_MENU),
}

/**
 * What a gesture does. The canvas runs the ones about the drawing itself; the others
 * ([QUICK_MENU], [COMMAND_PALETTE], [TOGGLE_CHROME]) go to the screen around it.
 */
enum class GestureAction(@StringRes val label: Int) {
    NONE(R.string.gesture_action_none),
    UNDO(R.string.action_undo),
    REDO(R.string.action_redo),
    /** Picks the colour under the finger. */
    EYEDROPPER(R.string.tool_name_eyedropper),
    /** Switches between the pen and the eraser. */
    TOGGLE_ERASER(R.string.gesture_action_toggle_eraser),
    FIT_VIEW(R.string.gesture_action_fit),
    QUICK_MENU(R.string.quick_menu),
    COMMAND_PALETTE(R.string.palette_title),
    /** Hides or shows every bar and panel, for a clear canvas. */
    TOGGLE_CHROME(R.string.gesture_action_toggle_chrome),
}

private const val KEY_GESTURES = "gesture_map"

/** The user's gesture map; defaults where unchanged. */
object GestureSettings {
    private var loaded: MutableState<Map<CanvasGesture, GestureAction>>? = null

    fun state(context: Context): MutableState<Map<CanvasGesture, GestureAction>> = loaded ?: mutableStateOf(read(context)).also { loaded = it }

    fun read(context: Context): Map<CanvasGesture, GestureAction> {
        val stored = AppSettings.prefs(context).getString(KEY_GESTURES, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        return CanvasGesture.entries.associateWith { g ->
            stored?.optString(g.name)?.let { name -> GestureAction.entries.firstOrNull { it.name == name } } ?: g.default
        }
    }

    fun set(context: Context, gesture: CanvasGesture, action: GestureAction) {
        val map = state(context).value + (gesture to action)
        AppSettings.prefs(context).edit().putString(KEY_GESTURES, JSONObject(map.mapKeys { it.key.name }.mapValues { it.value.name }).toString()).apply()
        state(context).value = map
    }
}

/** Each gesture with the action it runs, changeable in place (Settings, design roadmap D15). */
@Composable
fun GestureMapEditor() {
    val context = LocalContext.current
    val map by GestureSettings.state(context)
    for (gesture in CanvasGesture.entries) {
        var open by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(gesture.label), modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { open = true }) { Text(stringResource(map.getValue(gesture).label)) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (action in GestureAction.entries) {
                        DropdownMenuItem(text = { Text(stringResource(action.label)) }, onClick = {
                            GestureSettings.set(context, gesture, action)
                            open = false
                        })
                    }
                }
            }
        }
    }
}
