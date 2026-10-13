package com.andene.fern.canvas

import com.andene.fern.FernMessages
import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.FernTextButton
import com.andene.fern.R

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** A part of the layout that can be put back as it started (design roadmap D85). */
enum class LayoutSurface(@StringRes val label: Int) {
    RAIL(R.string.layout_rail),
    TOOLBAR(R.string.layout_toolbar),
    QUICK_MENU(R.string.quick_menu),
    GESTURES(R.string.layout_gestures),
    SHORTCUTS(R.string.shortcut_sheet_title),
}

/** The layout Fern starts with. */
val DEFAULT_LAYOUT = LayoutPreset(
    "default", DEFAULT_PINNED_TOOLS, emptySet(), DEFAULT_QUICK_MENU,
    CanvasGesture.entries.associateWith { it.default }, ShortcutAction.entries.associateWith { it.default },
)

/** [this] layout with [surfaces] put back as Fern starts them, the rest unchanged. */
fun LayoutPreset.withDefaults(surfaces: Set<LayoutSurface>): LayoutPreset = copy(
    pinned = if (LayoutSurface.RAIL in surfaces) DEFAULT_LAYOUT.pinned else pinned,
    hiddenGroups = if (LayoutSurface.TOOLBAR in surfaces) DEFAULT_LAYOUT.hiddenGroups else hiddenGroups,
    quickMenu = if (LayoutSurface.QUICK_MENU in surfaces) DEFAULT_LAYOUT.quickMenu else quickMenu,
    gestures = if (LayoutSurface.GESTURES in surfaces) DEFAULT_LAYOUT.gestures else gestures,
    shortcuts = if (LayoutSurface.SHORTCUTS in surfaces) DEFAULT_LAYOUT.shortcuts else shortcuts,
)

/** The surfaces of [this] that [other] would change. */
fun LayoutPreset.differencesFrom(other: LayoutPreset): Set<LayoutSurface> = buildSet {
    if (pinned != other.pinned) add(LayoutSurface.RAIL)
    if (hiddenGroups != other.hiddenGroups) add(LayoutSurface.TOOLBAR)
    if (quickMenu != other.quickMenu) add(LayoutSurface.QUICK_MENU)
    if (gestures != other.gestures) add(LayoutSurface.GESTURES)
    if (shortcuts != other.shortcuts) add(LayoutSurface.SHORTCUTS)
}

/**
 * Putting [surfaces] back as they started (design roadmap D85): first a preview of what would
 * change - each surface that differs, with what it would become - then Reset applies it, and the
 * message after it offers Undo, which brings back the layout as it was.
 */
@Composable
fun LayoutResetPanel(
    surfaces: Set<LayoutSurface>,
    current: LayoutPreset,
    onHiddenGroups: (Set<ToolbarGroup>) -> Unit,
    onQuickMenu: (List<QuickMenuAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val target = current.withDefaults(surfaces)
    val changing = current.differencesFrom(target)
    val resetDone = stringResource(R.string.layout_reset_done)
    val undo = stringResource(R.string.action_undo)
    FernPanel(
        onDismissRequest = onDismiss,
        scrollContent = true,
        title = { Text(stringResource(R.string.layout_reset_title)) },
        text = {
            Column {
                if (changing.isEmpty()) Text(stringResource(R.string.layout_reset_nothing))
                for (surface in changing) {
                    val becomes = when (surface) {
                        LayoutSurface.RAIL -> target.pinned.joinToString { stringResource(toolName(it)) }.ifEmpty { "—" }
                        LayoutSurface.TOOLBAR -> stringResource(R.string.layout_reset_all_groups)
                        LayoutSurface.QUICK_MENU -> target.quickMenu.joinToString { stringResource(it.label) }
                        LayoutSurface.GESTURES -> stringResource(R.string.layout_reset_default_gestures)
                        LayoutSurface.SHORTCUTS -> stringResource(R.string.layout_reset_default_keys)
                    }
                    Text(stringResource(R.string.layout_reset_line, stringResource(surface.label), becomes), modifier = Modifier.padding(bottom = FernScale.s8))
                }
            }
        },
        confirmButton = {
            FernTextButton(enabled = changing.isNotEmpty(), onClick = {
                LayoutPresets.apply(context, target, onHiddenGroups, onQuickMenu)
                FernMessages.show(resetDone, undo, confirmation = true) { LayoutPresets.apply(context, current, onHiddenGroups, onQuickMenu) }
                onDismiss()
            }) { Text(stringResource(R.string.layout_reset_apply)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
