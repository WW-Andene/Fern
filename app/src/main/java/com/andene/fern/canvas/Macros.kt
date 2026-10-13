package com.andene.fern.canvas

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** A recorded sequence of actions, run again in one go (design roadmap D34), on its own [key] when it has one. */
data class Macro(val name: String, val steps: List<QuickMenuAction>, val key: KeyCombo? = null)

private const val KEY_MACROS = "macros"

/**
 * Action history for repeating and recording (design roadmap D34): every action run from the
 * quick menu or the command palette goes through [perform], which remembers it as the last
 * action and, while recording, adds it to the macro being made.
 */
object Macros {
    var last by mutableStateOf<QuickMenuAction?>(null)
        private set
    var recording by mutableStateOf<List<QuickMenuAction>?>(null)
        private set

    fun perform(state: CanvasState, action: QuickMenuAction) {
        action.run(state)
        last = action
        recording?.let { recording = it + action }
    }

    /** Runs the last action again; false when there's none yet. */
    fun repeatLast(state: CanvasState): Boolean {
        val action = last ?: return false
        perform(state, action)
        return true
    }

    fun startRecording() {
        recording = emptyList()
    }

    /** Stops recording and gives back what was recorded (null if nothing). */
    fun stopRecording(): List<QuickMenuAction>? = recording.also { recording = null }?.takeIf { it.isNotEmpty() }

    fun run(state: CanvasState, macro: Macro) {
        for (step in macro.steps) step.run(state)
    }

    fun saved(context: Context): List<Macro> = AppSettings.prefs(context).getString(KEY_MACROS, null)
        ?.let { runCatching { JSONArray(it) }.getOrNull() }
        ?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val steps = o.optJSONArray("steps")?.let { s -> (0 until s.length()).mapNotNull { k -> QuickMenuAction.entries.firstOrNull { it.name == s.optString(k) } } }.orEmpty()
                val key = o.optJSONObject("key")?.let { KeyCombo(it.optInt("key"), it.optBoolean("ctrl"), it.optBoolean("shift")) }
                Macro(o.optString("name"), steps, key).takeIf { steps.isNotEmpty() }
            }
        }.orEmpty()

    fun setSaved(context: Context, macros: List<Macro>) {
        val json = JSONArray(
            macros.map { m ->
                JSONObject().put("name", m.name).put("steps", JSONArray(m.steps.map { it.name })).apply {
                    m.key?.let { put("key", JSONObject().put("key", it.keyCode).put("ctrl", it.ctrl).put("shift", it.shift)) }
                }
            },
        )
        AppSettings.prefs(context).edit().putString(KEY_MACROS, json.toString()).apply()
    }

    /**
     * Gives macro [name] the key [combo] (design roadmap D34). A key does one thing: the shortcut
     * or other macro that had it is left without one.
     */
    fun setKey(context: Context, name: String, combo: KeyCombo) {
        ShortcutSettings.actionFor(ShortcutSettings.state(context).value, combo)?.let { ShortcutSettings.set(context, it, UNBOUND) }
        setSaved(context, saved(context).map { m -> if (m.name == name) m.copy(key = combo) else if (m.key == combo) m.copy(key = null) else m })
    }

    /** Takes [combo] from whichever macro has it (a shortcut is being given it). */
    fun releaseKey(context: Context, combo: KeyCombo) {
        val macros = saved(context)
        if (macros.any { it.key == combo }) setSaved(context, macros.map { if (it.key == combo) it.copy(key = null) else it })
    }

    /** The saved macro on [combo], if any. */
    fun forKey(context: Context, combo: KeyCombo): Macro? = saved(context).firstOrNull { it.key == combo }
}
