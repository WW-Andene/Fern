package com.andene.fern.canvas

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * A tool saved with its settings under a name (design roadmap D84) - "Ink 2 px blue", "Soft
 * eraser" - pinned to the rail like a tool. Choosing it switches to [tool] with exactly [settings].
 */
data class RailPreset(val id: String, val name: String, val tool: Tool, val settings: ToolPreset)

private const val KEY_RAIL_PRESETS = "rail_presets"

/** The rail's named presets, in rail order; shared by every document. */
object RailPresets {
    private var loaded: MutableState<List<RailPreset>>? = null

    /** The presets as state, so the rail follows a change made in the layout editor. */
    fun state(context: Context): MutableState<List<RailPreset>> = loaded ?: mutableStateOf(read(context)).also { loaded = it }

    private fun read(context: Context): List<RailPreset> {
        val raw = AppSettings.prefs(context).getString(KEY_RAIL_PRESETS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val tool = Tool.entries.firstOrNull { it.name == o.optString("tool") } ?: return@mapNotNull null
            val settings = o.optJSONObject("settings")?.let(AppSettings::toolPresetFromJson) ?: return@mapNotNull null
            RailPreset(o.optString("id"), o.optString("name"), tool, settings)
        }
    }

    fun set(context: Context, presets: List<RailPreset>) {
        val array = JSONArray(presets.map { p -> JSONObject().put("id", p.id).put("name", p.name).put("tool", p.tool.name).put("settings", AppSettings.toolPresetToJson(p.settings)) })
        AppSettings.prefs(context).edit().putString(KEY_RAIL_PRESETS, array.toString()).apply()
        state(context).value = presets
    }
}
