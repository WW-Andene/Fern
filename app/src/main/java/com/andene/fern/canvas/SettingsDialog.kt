package com.andene.fern.canvas

import com.andene.fern.FernMessages

import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val PREFS_NAME = "settings"
private const val KEY_LEFT_HANDED = "left_handed"
private const val KEY_TOOLBAR_BOTTOM = "toolbar_bottom"
private const val KEY_STAMP_BRUSH = "stamp_brush"
private const val KEY_TOOL_SLOTS = "tool_slots"
private const val KEY_SNAP_TO_OBJECTS = "snap_to_objects"
private const val KEY_BRUSH_PRESETS = "brush_presets"
private const val KEY_HIGH_CONTRAST = "high_contrast"
private const val KEY_COLOR_BLIND_SAFE = "color_blind_safe"
private const val KEY_HAPTICS = "haptics"
private const val KEY_HIDDEN_TOOLBAR_GROUPS = "hidden_toolbar_groups"
private const val KEY_QUICK_MENU = "quick_menu_actions"

/** App preferences, stored in SharedPreferences so they apply to every document and survive restarts. */
object AppSettings {
    /** The settings file, for settings kept beside the code that uses them (e.g. [RailSettings]). */
    fun prefs(context: Context): android.content.SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun leftHanded(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_LEFT_HANDED, false)

    fun setLeftHanded(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_LEFT_HANDED, value).apply()
    }

    fun toolbarAtBottom(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TOOLBAR_BOTTOM, false)

    fun setToolbarAtBottom(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_TOOLBAR_BOTTOM, value).apply()
    }

    fun highContrast(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HIGH_CONTRAST, false)

    fun setHighContrast(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()
    }

    /** Haptic feedback (design roadmap D7), on unless switched off. */
    fun haptics(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HAPTICS, true)

    fun setHaptics(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_HAPTICS, value).apply()
    }

    /** Colour-blind-safe marks on the canvas (design roadmap D2). */
    fun colorBlindSafe(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_COLOR_BLIND_SAFE, false)

    fun setColorBlindSafe(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_COLOR_BLIND_SAFE, value).apply()
    }

    /** Toolbar groups the user hid; names no longer in [ToolbarGroup] (from an older version) are ignored. */
    fun hiddenToolbarGroups(context: Context): Set<ToolbarGroup> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(KEY_HIDDEN_TOOLBAR_GROUPS, emptySet()).orEmpty()
            .mapNotNullTo(HashSet()) { name -> ToolbarGroup.entries.firstOrNull { it.name == name } }

    /** The quick menu's actions (roadmap item 133), in ring order; [DEFAULT_QUICK_MENU] until changed. */
    fun quickMenuActions(context: Context): List<QuickMenuAction> {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_QUICK_MENU, null) ?: return DEFAULT_QUICK_MENU
        // Names no longer in QuickMenuAction (from another version) are dropped.
        val names = saved.split(',').toSet()
        return QuickMenuAction.entries.filter { it.name in names }.ifEmpty { DEFAULT_QUICK_MENU }
    }

    fun setQuickMenuActions(context: Context, value: List<QuickMenuAction>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_QUICK_MENU, value.joinToString(",") { it.name }).apply()
    }

    fun setHiddenToolbarGroups(context: Context, value: Set<ToolbarGroup>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putStringSet(KEY_HIDDEN_TOOLBAR_GROUPS, value.mapTo(HashSet()) { it.name }).apply()
    }

    fun snapToObjects(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SNAP_TO_OBJECTS, true)

    fun setSnapToObjects(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SNAP_TO_OBJECTS, value).apply()
    }

    /** The last stamp brush chosen in the brush editor, or the default brush if none was (or it can't be read). */
    fun stampBrush(context: Context): StampBrush {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_STAMP_BRUSH, null) ?: return StampBrush()
        return try {
            brushFromJson(JSONObject(raw))
        } catch (e: JSONException) {
            StampBrush()
        }
    }

    fun setStampBrush(context: Context, brush: StampBrush) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_STAMP_BRUSH, brushToJson(brush).toString()).apply()
    }

    /** Favourite tool slots; a slot that can't be read (or was never set) comes back empty. */
    fun toolSlots(context: Context): List<ToolPreset?> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TOOL_SLOTS, null) ?: return emptyList()
        val array = try { JSONArray(raw) } catch (e: JSONException) { return emptyList() }
        return (0 until array.length()).map { i ->
            val json = array.optJSONObject(i) ?: return@map null
            try {
                ToolPreset(
                    penType = PenType.entries.firstOrNull { it.name == json.getString("penType") } ?: PenType.MARKER,
                    color = Color(json.getInt("color")),
                    width = json.getDouble("width"),
                    opacity = json.getDouble("opacity"),
                    blendMode = StrokeBlendMode.entries.firstOrNull { it.name == json.optString("blendMode") } ?: StrokeBlendMode.NORMAL,
                    dashed = json.optBoolean("dashed", false),
                    brush = json.optJSONObject("brush")?.let(::brushFromJson) ?: StampBrush(),
                )
            } catch (e: JSONException) {
                null
            }
        }
    }

    fun setToolSlots(context: Context, slots: List<ToolPreset?>) {
        val array = JSONArray()
        for (slot in slots) {
            if (slot == null) {
                array.put(JSONObject.NULL)
                continue
            }
            array.put(
                JSONObject()
                    .put("penType", slot.penType.name)
                    .put("color", slot.color.toArgb())
                    .put("width", slot.width)
                    .put("opacity", slot.opacity)
                    .put("blendMode", slot.blendMode.name)
                    .put("dashed", slot.dashed)
                    .put("brush", brushToJson(slot.brush)),
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_TOOL_SLOTS, array.toString()).apply()
    }

    /** Saved stamp-brush presets; entries that can't be read are skipped. */
    fun brushPresets(context: Context): List<BrushPreset> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_BRUSH_PRESETS, null) ?: return emptyList()
        val array = try { JSONArray(raw) } catch (e: JSONException) { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tags = json.optJSONArray("tags")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
            BrushPreset(name, json.optJSONObject("brush")?.let(::brushFromJson) ?: return@mapNotNull null, tags)
        }
    }

    fun setBrushPresets(context: Context, presets: List<BrushPreset>) {
        val array = JSONArray()
        for (preset in presets) {
            array.put(JSONObject().put("name", preset.name).put("brush", brushToJson(preset.brush)).put("tags", JSONArray(preset.tags)))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_BRUSH_PRESETS, array.toString()).apply()
    }

    private fun brushToJson(brush: StampBrush): JSONObject = JSONObject()
        .put("tip", brush.tip.name)
        .put("spacing", brush.spacing.toDouble())
        .put("scatter", brush.scatter.toDouble())
        .put("sizeJitter", brush.sizeJitter.toDouble())
        .put("angleJitter", brush.angleJitter.toDouble())
        .put("followDirection", brush.followDirection)
        .apply { brush.mask?.let { put("maskSize", it.size).put("mask", android.util.Base64.encodeToString(it.alpha, android.util.Base64.NO_WRAP)) } }

    private fun brushFromJson(json: JSONObject): StampBrush = StampBrush(
        tip = StampTip.entries.firstOrNull { it.name == json.optString("tip") } ?: StampTip.CIRCLE,
        spacing = json.optDouble("spacing", 0.5).toFloat(),
        scatter = json.optDouble("scatter", 0.0).toFloat(),
        sizeJitter = json.optDouble("sizeJitter", 0.0).toFloat(),
        angleJitter = json.optDouble("angleJitter", 0.0).toFloat(),
        followDirection = json.optBoolean("followDirection", false),
        mask = runCatching { TipMask(json.getInt("maskSize"), android.util.Base64.decode(json.getString("mask"), android.util.Base64.NO_WRAP)) }.getOrNull(),
    ).clamped()
}

/**
 * Settings panel. Floating controls (the minimap) sit on the side opposite the drawing hand,
 * whose palm rests below and beside the pen: bottom-left by default, bottom-right in
 * left-handed mode - the Procreate convention. "Show the gesture guide" reopens the
 * first-launch guide.
 */
@Composable
fun SettingsDialog(
    leftHanded: Boolean,
    onLeftHandedChange: (Boolean) -> Unit,
    toolbarAtBottom: Boolean,
    onToolbarAtBottomChange: (Boolean) -> Unit,
    highContrast: Boolean,
    onHighContrastChange: (Boolean) -> Unit,
    colorBlindSafe: Boolean,
    onColorBlindSafeChange: (Boolean) -> Unit,
    haptics: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    snapToObjects: Boolean,
    onSnapToObjectsChange: (Boolean) -> Unit,
    onShowGuide: () -> Unit,
    onCustomizeToolbar: () -> Unit,
    onCustomizeQuickMenu: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_left_handed), modifier = Modifier.weight(1f))
                    Switch(checked = leftHanded, onCheckedChange = onLeftHandedChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_toolbar_bottom), modifier = Modifier.weight(1f))
                    Switch(checked = toolbarAtBottom, onCheckedChange = onToolbarAtBottomChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_high_contrast), modifier = Modifier.weight(1f))
                    Switch(checked = highContrast, onCheckedChange = onHighContrastChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_color_blind_safe), modifier = Modifier.weight(1f))
                    Switch(checked = colorBlindSafe, onCheckedChange = onColorBlindSafeChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_haptics), modifier = Modifier.weight(1f))
                    Switch(checked = haptics, onCheckedChange = onHapticsChange)
                }
                // Gestures (design roadmap D15).
                Text(stringResource(R.string.settings_gestures), style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FernScale.s8))
                GestureMapEditor()
                PalmRejectionEditor()
                // Keyboard shortcuts (design roadmap D18).
                Text(stringResource(R.string.settings_shortcuts), style = androidx.compose.material3.MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FernScale.s8))
                ShortcutEditor()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_snap_objects), modifier = Modifier.weight(1f))
                    Switch(checked = snapToObjects, onCheckedChange = onSnapToObjectsChange)
                }
                CmykProfileRow()
                TextButton(onClick = onCustomizeToolbar) { Text(stringResource(R.string.settings_customize_toolbar)) }
                TextButton(onClick = onCustomizeQuickMenu) { Text(stringResource(R.string.quick_menu_customize)) }
                TextButton(onClick = onShowGuide) { Text(stringResource(R.string.settings_show_guide)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

/** The CMYK ICC profile used for print colours (roadmap item 102): which one, load another, or go back to device CMYK. */
@Composable
private fun CmykProfileRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
            ColorManagement.installProfile(context, bytes)
        }.isSuccess
        if (!ok) FernMessages.show(context.getString(R.string.settings_cmyk_bad))
    }
    val profile = ColorManagement.cmykProfile
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.settings_cmyk_profile, profile?.description?.ifEmpty { "ICC" } ?: stringResource(R.string.settings_cmyk_none)),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { pick.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.settings_cmyk_load)) }
        if (profile != null) TextButton(onClick = { ColorManagement.removeProfile(context) }) { Text(stringResource(R.string.settings_cmyk_remove)) }
    }
}
