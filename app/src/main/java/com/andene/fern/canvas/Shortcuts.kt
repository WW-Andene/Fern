package com.andene.fern.canvas

import android.content.Context
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andene.fern.FernScale
import com.andene.fern.FernSpace
import com.andene.fern.R
import org.json.JSONObject

/** A key with its modifiers, as Android key codes. */
data class KeyCombo(val keyCode: Int, val ctrl: Boolean = false, val shift: Boolean = false) {
    /** How it reads: "Ctrl+Shift+Z"; a dash when unbound. */
    fun label(): String = if (keyCode == KeyEvent.KEYCODE_UNKNOWN) "—" else buildString {
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        append(KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace("LEFT_BRACKET", "[").replace("RIGHT_BRACKET", "]").replace("FORWARD_DEL", "Del"))
    }
}

/**
 * Everything a key can do (design roadmap D18), with its default key. Tool keys can also be
 * held to borrow the tool (D17).
 */
enum class ShortcutAction(@StringRes val label: Int, val default: KeyCombo, val tool: Tool? = null) {
    PEN(R.string.tool_name_pen, KeyCombo(KeyEvent.KEYCODE_P), Tool.PEN),
    BRUSH(R.string.tool_name_pen, KeyCombo(KeyEvent.KEYCODE_B), Tool.PEN),
    ERASER(R.string.tool_name_eraser, KeyCombo(KeyEvent.KEYCODE_E), Tool.ERASER),
    SELECT(R.string.tool_name_select, KeyCombo(KeyEvent.KEYCODE_V), Tool.SELECT),
    SHAPE(R.string.tool_name_shape, KeyCombo(KeyEvent.KEYCODE_U), Tool.SHAPE),
    FILL(R.string.tool_name_fill, KeyCombo(KeyEvent.KEYCODE_G), Tool.FILL),
    TEXT(R.string.tool_name_text, KeyCombo(KeyEvent.KEYCODE_T), Tool.TEXT),
    EYEDROPPER(R.string.tool_name_eyedropper, KeyCombo(KeyEvent.KEYCODE_I), Tool.EYEDROPPER),
    BEZIER(R.string.tool_name_bezier, KeyCombo(KeyEvent.KEYCODE_N), Tool.BEZIER),
    MEASURE(R.string.tool_name_measure, KeyCombo(KeyEvent.KEYCODE_M), Tool.MEASURE),
    UNDO(R.string.action_undo, KeyCombo(KeyEvent.KEYCODE_Z, ctrl = true)),
    REDO(R.string.action_redo, KeyCombo(KeyEvent.KEYCODE_Y, ctrl = true)),
    REDO_ALT(R.string.action_redo, KeyCombo(KeyEvent.KEYCODE_Z, ctrl = true, shift = true)),
    COPY(R.string.shortcut_copy, KeyCombo(KeyEvent.KEYCODE_C, ctrl = true)),
    PASTE(R.string.shortcut_paste, KeyCombo(KeyEvent.KEYCODE_V, ctrl = true)),
    DUPLICATE(R.string.shortcut_duplicate, KeyCombo(KeyEvent.KEYCODE_D, ctrl = true)),
    DELETE(R.string.tb_delete, KeyCombo(KeyEvent.KEYCODE_FORWARD_DEL)),
    DELETE_BACK(R.string.tb_delete, KeyCombo(KeyEvent.KEYCODE_DEL)),
    SIZE_DOWN(R.string.shortcut_size_down, KeyCombo(KeyEvent.KEYCODE_LEFT_BRACKET)),
    SIZE_UP(R.string.shortcut_size_up, KeyCombo(KeyEvent.KEYCODE_RIGHT_BRACKET)),
    ZOOM_IN(R.string.shortcut_zoom_in, KeyCombo(KeyEvent.KEYCODE_EQUALS, ctrl = true)),
    ZOOM_OUT(R.string.shortcut_zoom_out, KeyCombo(KeyEvent.KEYCODE_MINUS, ctrl = true)),
    FIT(R.string.gesture_action_fit, KeyCombo(KeyEvent.KEYCODE_0)),
    PALETTE(R.string.palette_title, KeyCombo(KeyEvent.KEYCODE_K, ctrl = true)),
    QUICK_MENU(R.string.quick_menu, KeyCombo(KeyEvent.KEYCODE_Q)),
    REPEAT_LAST(R.string.macro_repeat_last, KeyCombo(KeyEvent.KEYCODE_PERIOD, ctrl = true)),
    TOGGLE_CHROME(R.string.gesture_action_toggle_chrome, KeyCombo(KeyEvent.KEYCODE_TAB)),
}

private const val KEY_SHORTCUTS = "shortcuts"

/** No key. */
val UNBOUND = KeyCombo(KeyEvent.KEYCODE_UNKNOWN)

/** Whether the cheat sheet shows (while Ctrl is held). */
object ShortcutCapture {
    var cheatSheet by mutableStateOf(false)
}

/** The user's key map; defaults where unchanged. */
object ShortcutSettings {
    private var loaded: MutableState<Map<ShortcutAction, KeyCombo>>? = null

    fun state(context: Context): MutableState<Map<ShortcutAction, KeyCombo>> = loaded ?: mutableStateOf(read(context)).also { loaded = it }

    fun read(context: Context): Map<ShortcutAction, KeyCombo> {
        val stored = AppSettings.prefs(context).getString(KEY_SHORTCUTS, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        return ShortcutAction.entries.associateWith { a ->
            stored?.optJSONObject(a.name)?.let { KeyCombo(it.optInt("key"), it.optBoolean("ctrl"), it.optBoolean("shift")) } ?: a.default
        }
    }

    fun set(context: Context, action: ShortcutAction, combo: KeyCombo) {
        // A key does one thing: an action that had it is left without a key.
        val map = state(context).value.mapValues { (a, c) -> if (c == combo && a != action) UNBOUND else c }.toMutableMap()
        map[action] = combo
        val o = JSONObject()
        for ((a, c) in map) o.put(a.name, JSONObject().put("key", c.keyCode).put("ctrl", c.ctrl).put("shift", c.shift))
        AppSettings.prefs(context).edit().putString(KEY_SHORTCUTS, o.toString()).apply()
        state(context).value = map
    }

    /** The action bound to [combo] in [map], if any. */
    fun actionFor(map: Map<ShortcutAction, KeyCombo>, combo: KeyCombo): ShortcutAction? = map.entries.firstOrNull { it.value == combo }?.key
}

/**
 * The key map in Settings (design roadmap D18): tap an action, then press the key for it; the
 * press is heard here, since Settings is its own window.
 */
@Composable
fun ShortcutEditor(filter: (String) -> Boolean = { true }) {
    val context = LocalContext.current
    val map by ShortcutSettings.state(context)
    var capturing by remember { mutableStateOf<ShortcutAction?>(null) }
    val focus = remember { FocusRequester() }
    Column(
        Modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val action = capturing ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val code = event.nativeKeyEvent.keyCode
                if (code in setOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT)) {
                    return@onPreviewKeyEvent true
                }
                ShortcutSettings.set(context, action, KeyCombo(code, event.isCtrlPressed || event.isMetaPressed, event.isShiftPressed))
                capturing = null
                true
            },
    ) {
        for (action in ShortcutAction.entries) {
            val name = stringResource(action.label)
            if (!filter(name)) continue
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, modifier = Modifier.weight(1f))
                if (map.getValue(action) != action.default) {
                    IconButton(onClick = { ShortcutSettings.set(context, action, action.default) }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.settings_reset, name))
                    }
                }
                TextButton(onClick = {
                    capturing = if (capturing == action) null else action
                    runCatching { focus.requestFocus() }
                }) {
                    Text(if (capturing == action) stringResource(R.string.shortcut_press_key) else map.getValue(action).label())
                }
            }
        }
    }
}

/** The shortcuts at a glance, shown while Ctrl is held (design roadmap D18). */
@Composable
fun ShortcutCheatSheet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val map by ShortcutSettings.state(context)
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = FernSpace.sm, shadowElevation = FernSpace.sm) {
        Column(Modifier.padding(FernSpace.lg)) {
            Text(stringResource(R.string.shortcut_sheet_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = FernScale.s8))
            for ((action, combo) in map) {
                Row {
                    Text(combo.label(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = FernSpace.lg))
                    Text(stringResource(action.label), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
