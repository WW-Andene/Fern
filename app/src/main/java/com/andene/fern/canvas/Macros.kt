package com.andene.fern.canvas

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** A recorded sequence of actions, run again in one go (design roadmap D34). */
data class Macro(val name: String, val steps: List<QuickMenuAction>)

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
                Macro(o.optString("name"), steps).takeIf { steps.isNotEmpty() }
            }
        }.orEmpty()

    fun setSaved(context: Context, macros: List<Macro>) {
        AppSettings.prefs(context).edit().putString(KEY_MACROS, JSONArray(macros.map { m -> JSONObject().put("name", m.name).put("steps", JSONArray(m.steps.map { it.name })) }).toString()).apply()
    }
}
