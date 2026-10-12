package com.andene.fern.canvas

import android.content.Context
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
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

/** App preferences, stored in SharedPreferences so they apply to every document and survive restarts. */
object AppSettings {
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
            BrushPreset(name, json.optJSONObject("brush")?.let(::brushFromJson) ?: return@mapNotNull null)
        }
    }

    fun setBrushPresets(context: Context, presets: List<BrushPreset>) {
        val array = JSONArray()
        for (preset in presets) array.put(JSONObject().put("name", preset.name).put("brush", brushToJson(preset.brush)))
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_BRUSH_PRESETS, array.toString()).apply()
    }

    private fun brushToJson(brush: StampBrush): JSONObject = JSONObject()
        .put("tip", brush.tip.name)
        .put("spacing", brush.spacing.toDouble())
        .put("scatter", brush.scatter.toDouble())
        .put("sizeJitter", brush.sizeJitter.toDouble())
        .put("angleJitter", brush.angleJitter.toDouble())
        .put("followDirection", brush.followDirection)

    private fun brushFromJson(json: JSONObject): StampBrush = StampBrush(
        tip = StampTip.entries.firstOrNull { it.name == json.optString("tip") } ?: StampTip.CIRCLE,
        spacing = json.optDouble("spacing", 0.5).toFloat(),
        scatter = json.optDouble("scatter", 0.0).toFloat(),
        sizeJitter = json.optDouble("sizeJitter", 0.0).toFloat(),
        angleJitter = json.optDouble("angleJitter", 0.0).toFloat(),
        followDirection = json.optBoolean("followDirection", false),
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
    snapToObjects: Boolean,
    onSnapToObjectsChange: (Boolean) -> Unit,
    onShowGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Left-handed layout", modifier = Modifier.weight(1f))
                    Switch(checked = leftHanded, onCheckedChange = onLeftHandedChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Toolbar at the bottom", modifier = Modifier.weight(1f))
                    Switch(checked = toolbarAtBottom, onCheckedChange = onToolbarAtBottomChange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Snap shapes to objects", modifier = Modifier.weight(1f))
                    Switch(checked = snapToObjects, onCheckedChange = onSnapToObjectsChange)
                }
                TextButton(onClick = onShowGuide) { Text("Show the gesture guide") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
