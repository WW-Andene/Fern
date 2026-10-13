package com.andene.fern.canvas

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.andene.fern.FernScale
import com.andene.fern.FernSpace
import com.andene.fern.R

/**
 * A tip shown once, the first time it's relevant (design roadmap D29): on first launch, or the
 * first time its [tool] is picked. All of them are listed again in the tips list ([WelcomeDialog]).
 */
enum class Tip(@StringRes val text: Int, val tool: Tool? = null) {
    NAVIGATE(R.string.welcome_tip_navigate),
    UNDO(R.string.welcome_tip_undo),
    PEN(R.string.tip_pen, Tool.PEN),
    ERASER(R.string.tip_eraser, Tool.ERASER),
    SELECT(R.string.tip_select, Tool.SELECT),
    SHAPE(R.string.welcome_tip_shapes, Tool.SHAPE),
    FILL(R.string.tip_fill, Tool.FILL),
    TEXT(R.string.tip_text, Tool.TEXT),
    EYEDROPPER(R.string.welcome_tip_eyedropper, Tool.EYEDROPPER),
    BEZIER(R.string.tip_bezier, Tool.BEZIER),
    MEASURE(R.string.tip_measure, Tool.MEASURE),
    BONE(R.string.tip_bone, Tool.BONE),
    CONNECT(R.string.tip_connect, Tool.CONNECT),
}

private const val PREFS = "onboarding"
private const val KEY_SEEN = "tips_seen"
private const val KEY_OFF = "tips_off"

object Tips {
    fun seen(context: Context): Set<String> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_SEEN, emptySet()).orEmpty()

    fun markSeen(context: Context, tip: Tip) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SEEN, seen(context) + tip.name).apply()
    }

    fun off(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFF, false)

    fun setOff(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OFF, value).apply()
    }

    /** The tip to show now: the first-launch ones first, then the active tool's; null when none is due. */
    fun due(context: Context, tool: Tool, seen: Set<String>): Tip? {
        if (off(context)) return null
        return Tip.entries.firstOrNull { it.tool == null && it.name !in seen } ?: Tip.entries.firstOrNull { it.tool == tool && it.name !in seen }
    }
}

/**
 * Shows the tip that's due as a small card that doesn't block the canvas: "Got it" retires it,
 * "No more tips" turns them all off (Settings brings them back through the tips list).
 */
@Composable
fun CoachMarkHost(state: CanvasState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var seen by remember { mutableStateOf(Tips.seen(context)) }
    var off by remember { mutableStateOf(Tips.off(context)) }
    if (off) return
    val tip = Tips.due(context, state.activeTool, seen) ?: return
    Surface(
        modifier.widthIn(max = FernScale.s384).semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = FernSpace.sm,
    ) {
        Column(Modifier.padding(start = FernSpace.lg, end = FernSpace.sm, top = FernSpace.md)) {
            Text(stringResource(tip.text), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = {
                    Tips.setOff(context, true)
                    off = true
                }) { Text(stringResource(R.string.tips_off), color = MaterialTheme.colorScheme.inversePrimary) }
                TextButton(onClick = {
                    Tips.markSeen(context, tip)
                    seen = seen + tip.name
                }) { Text(stringResource(R.string.tips_got_it), color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
    }
}
