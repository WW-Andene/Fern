package com.andene.fern.canvas

import android.content.Context
import androidx.annotation.StringRes
import com.andene.fern.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * A whole layout (design roadmap D23): the rail's pinned tools, the toolbar groups hidden, the
 * quick menu, the gesture map and the key map - everything the user arranges. Saved by name,
 * exported to a file and imported back; built-in ones have a translated [label].
 */
data class LayoutPreset(
    val name: String,
    val pinned: List<Tool>,
    val hiddenGroups: Set<ToolbarGroup>,
    val quickMenu: List<QuickMenuAction>,
    val gestures: Map<CanvasGesture, GestureAction>,
    val shortcuts: Map<ShortcutAction, KeyCombo>,
    @StringRes val label: Int? = null,
)

private val DEFAULT_GESTURES = CanvasGesture.entries.associateWith { it.default }
private val DEFAULT_SHORTCUTS = ShortcutAction.entries.associateWith { it.default }

/** Ready-made layouts. */
val BUILT_IN_LAYOUTS = listOf(
    // Only what drawing needs: no toolbar groups, the rail and the quick menu do the rest.
    LayoutPreset("minimal", listOf(Tool.FILL, Tool.EYEDROPPER), ToolbarGroup.entries.toSet() - ToolbarGroup.VIEW,
        listOf(QuickMenuAction.PEN, QuickMenuAction.ERASER, QuickMenuAction.UNDO, QuickMenuAction.REDO), DEFAULT_GESTURES, DEFAULT_SHORTCUTS, R.string.layout_preset_minimal),
    // Two-finger undo, three-finger redo, held finger picks colour, four fingers clear the screen.
    LayoutPreset("procreate", listOf(Tool.FILL, Tool.EYEDROPPER), setOf(ToolbarGroup.REPLAY, ToolbarGroup.BACKGROUND),
        listOf(QuickMenuAction.PEN, QuickMenuAction.ERASER, QuickMenuAction.SELECT, QuickMenuAction.SHAPES, QuickMenuAction.EYEDROPPER, QuickMenuAction.FILL),
        DEFAULT_GESTURES + (CanvasGesture.FOUR_FINGER_TAP to GestureAction.TOGGLE_CHROME), DEFAULT_SHORTCUTS, R.string.layout_preset_procreate),
    // Everything shown, many pins, gestures kept simple for mouse and keyboard.
    LayoutPreset("desktop", listOf(Tool.FILL, Tool.TEXT, Tool.EYEDROPPER, Tool.BEZIER, Tool.MEASURE), emptySet(), DEFAULT_QUICK_MENU,
        DEFAULT_GESTURES + (CanvasGesture.LONG_PRESS to GestureAction.NONE), DEFAULT_SHORTCUTS, R.string.layout_preset_desktop),
    // A phone in one hand: the quick menu flick does most, a held finger switches pen and eraser.
    LayoutPreset("one_hand", listOf(Tool.FILL), ToolbarGroup.entries.toSet() - setOf(ToolbarGroup.VIEW, ToolbarGroup.FAVOURITES),
        listOf(QuickMenuAction.PEN, QuickMenuAction.ERASER, QuickMenuAction.UNDO, QuickMenuAction.REDO, QuickMenuAction.BIGGER, QuickMenuAction.SMALLER),
        DEFAULT_GESTURES + (CanvasGesture.LONG_PRESS to GestureAction.TOGGLE_ERASER), DEFAULT_SHORTCUTS, R.string.layout_preset_one_hand),
)

private const val KEY_LAYOUT_PRESETS = "layout_presets"

object LayoutPresets {
    /** The layout as it is now. */
    fun current(context: Context, name: String, hiddenGroups: Set<ToolbarGroup>, quickMenu: List<QuickMenuAction>) = LayoutPreset(
        name, RailSettings.pinned(context), hiddenGroups, quickMenu, GestureSettings.read(context), ShortcutSettings.read(context),
    )

    /** Makes [preset] the layout: everything stored, the toolbar and quick menu through the callbacks. */
    fun apply(context: Context, preset: LayoutPreset, onHiddenGroups: (Set<ToolbarGroup>) -> Unit, onQuickMenu: (List<QuickMenuAction>) -> Unit) {
        RailSettings.setPinned(context, preset.pinned)
        AppSettings.setHiddenToolbarGroups(context, preset.hiddenGroups)
        onHiddenGroups(preset.hiddenGroups)
        AppSettings.setQuickMenuActions(context, preset.quickMenu)
        onQuickMenu(preset.quickMenu)
        for ((g, a) in preset.gestures) GestureSettings.set(context, g, a)
        for ((a, c) in preset.shortcuts) ShortcutSettings.set(context, a, c)
    }

    fun saved(context: Context): List<LayoutPreset> = AppSettings.prefs(context).getString(KEY_LAYOUT_PRESETS, null)
        ?.let { runCatching { JSONArray(it) }.getOrNull() }
        ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::read) } }.orEmpty()

    fun setSaved(context: Context, presets: List<LayoutPreset>) {
        AppSettings.prefs(context).edit().putString(KEY_LAYOUT_PRESETS, JSONArray(presets.map(::write)).toString()).apply()
    }

    fun write(p: LayoutPreset): JSONObject = JSONObject()
        .put("fernLayout", 1)
        .put("name", p.name)
        .put("pinned", JSONArray(p.pinned.map { it.name }))
        .put("hidden", JSONArray(p.hiddenGroups.map { it.name }))
        .put("quickMenu", JSONArray(p.quickMenu.map { it.name }))
        .put("gestures", JSONObject(p.gestures.map { (k, v) -> k.name to v.name }.toMap()))
        .put("shortcuts", JSONObject().apply { for ((a, c) in p.shortcuts) put(a.name, JSONObject().put("key", c.keyCode).put("ctrl", c.ctrl).put("shift", c.shift)) })

    /** A layout from [write]'s form; unknown names (from another version) are skipped, missing parts take the defaults. */
    fun read(o: JSONObject): LayoutPreset? {
        if (!o.has("fernLayout")) return null
        fun names(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val gestures = o.optJSONObject("gestures")
        val shortcuts = o.optJSONObject("shortcuts")
        return LayoutPreset(
            o.optString("name"),
            names("pinned").mapNotNull { n -> Tool.entries.firstOrNull { it.name == n } },
            names("hidden").mapNotNullTo(HashSet()) { n -> ToolbarGroup.entries.firstOrNull { it.name == n } },
            names("quickMenu").mapNotNull { n -> QuickMenuAction.entries.firstOrNull { it.name == n } }.ifEmpty { DEFAULT_QUICK_MENU },
            CanvasGesture.entries.associateWith { g -> gestures?.optString(g.name)?.let { n -> GestureAction.entries.firstOrNull { it.name == n } } ?: g.default },
            ShortcutAction.entries.associateWith { a -> shortcuts?.optJSONObject(a.name)?.let { KeyCombo(it.optInt("key"), it.optBoolean("ctrl"), it.optBoolean("shift")) } ?: a.default },
        )
    }
}
