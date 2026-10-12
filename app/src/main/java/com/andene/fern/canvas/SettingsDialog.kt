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
import org.json.JSONException
import org.json.JSONObject

private const val PREFS_NAME = "settings"
private const val KEY_LEFT_HANDED = "left_handed"
private const val KEY_TOOLBAR_BOTTOM = "toolbar_bottom"
private const val KEY_STAMP_BRUSH = "stamp_brush"

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

    /** The last stamp brush chosen in the brush editor, or the default brush if none was (or it can't be read). */
    fun stampBrush(context: Context): StampBrush {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_STAMP_BRUSH, null) ?: return StampBrush()
        return try {
            val json = JSONObject(raw)
            StampBrush(
                tip = StampTip.entries.firstOrNull { it.name == json.optString("tip") } ?: StampTip.CIRCLE,
                spacing = json.optDouble("spacing", 0.5).toFloat(),
                scatter = json.optDouble("scatter", 0.0).toFloat(),
                sizeJitter = json.optDouble("sizeJitter", 0.0).toFloat(),
                angleJitter = json.optDouble("angleJitter", 0.0).toFloat(),
                followDirection = json.optBoolean("followDirection", false),
            ).clamped()
        } catch (e: JSONException) {
            StampBrush()
        }
    }

    fun setStampBrush(context: Context, brush: StampBrush) {
        val json = JSONObject()
            .put("tip", brush.tip.name)
            .put("spacing", brush.spacing.toDouble())
            .put("scatter", brush.scatter.toDouble())
            .put("sizeJitter", brush.sizeJitter.toDouble())
            .put("angleJitter", brush.angleJitter.toDouble())
            .put("followDirection", brush.followDirection)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_STAMP_BRUSH, json.toString()).apply()
    }
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
                TextButton(onClick = onShowGuide) { Text("Show the gesture guide") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
