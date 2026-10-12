package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * Choosing the quick menu's actions (roadmap item 133, as Procreate's QuickMenu is set up): a
 * chip per [QuickMenuAction], ticked ones on the ring, at least one and at most
 * [QUICK_MENU_MAX_ACTIONS]. Changes apply at once; Reset brings back [DEFAULT_QUICK_MENU].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickMenuCustomizeDialog(
    chosen: List<QuickMenuAction>,
    onChange: (List<QuickMenuAction>) -> Unit,
    state: CanvasState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_menu_customize)) },
        text = {
            Column {
                Text(stringResource(R.string.quick_menu_customize_hint, QUICK_MENU_MAX_ACTIONS))
                FlowRow(modifier = Modifier.padding(top = 8.dp)) {
                    for (action in QuickMenuAction.entries) {
                        val on = action in chosen
                        FilterChip(
                            selected = on,
                            onClick = { onChange(toggledQuickMenu(chosen, action)) },
                            enabled = on || chosen.size < QUICK_MENU_MAX_ACTIONS,
                            leadingIcon = { Icon(action.icon(state), contentDescription = null) },
                            label = { Text(stringResource(action.label)) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = { onChange(DEFAULT_QUICK_MENU) }) { Text(stringResource(R.string.quick_menu_reset)) } },
    )
}

/**
 * [chosen] with [action] added or removed, in [QuickMenuAction] order; the last one can't be
 * removed (an empty ring would be a button that does nothing) and no more than
 * [QUICK_MENU_MAX_ACTIONS] are kept.
 */
fun toggledQuickMenu(chosen: List<QuickMenuAction>, action: QuickMenuAction): List<QuickMenuAction> {
    val next = if (action in chosen) chosen - action else chosen + action
    if (next.isEmpty() || next.size > QUICK_MENU_MAX_ACTIONS) return chosen
    return QuickMenuAction.entries.filter { it in next }
}
