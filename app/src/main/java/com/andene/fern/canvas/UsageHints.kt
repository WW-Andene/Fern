package com.andene.fern.canvas

import android.content.Context
import com.andene.fern.FernMessages
import com.andene.fern.R

// Reached this many times the long way, a tool is worth offering a place on the rail.
private const val INDIRECT_USES_BEFORE_HINT = 5
private const val PREFS = "usage_hints"

/**
 * Suggestions from how the app is used (design roadmap D35), counted on the device and never
 * sent anywhere (CLAUDE.md §8.1): a tool picked again and again from "More tools" or the command
 * palette is offered a place on the rail, once.
 */
object UsageHints {
    fun toolReachedIndirectly(context: Context, tool: Tool) {
        if (tool in RAIL_TOOLS || tool in RailSettings.pinned(context) || tool !in RAIL_EXTRA_TOOLS) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("offered_${tool.name}", false)) return
        val uses = prefs.getInt("indirect_${tool.name}", 0) + 1
        prefs.edit().putInt("indirect_${tool.name}", uses).apply()
        if (uses < INDIRECT_USES_BEFORE_HINT) return
        prefs.edit().putBoolean("offered_${tool.name}", true).apply()
        val name = context.getString(toolName(tool))
        FernMessages.show(context.getString(R.string.hint_pin_tool, name), context.getString(R.string.hint_pin)) {
            RailSettings.setPinned(context, RailSettings.pinned(context) + tool)
        }
    }
}
