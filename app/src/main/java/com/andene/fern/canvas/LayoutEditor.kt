package com.andene.fern.canvas

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.FernSize
import com.andene.fern.R
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

/**
 * Arrange the layout (design roadmap D22), all in one panel: the rail's pinned tools and the
 * quick menu's actions, each reordered by dragging its handle, removed with one tap and added
 * from what's left; and which toolbar groups show. Changes apply at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutEditor(
    state: CanvasState,
    quickMenu: List<QuickMenuAction>,
    onQuickMenuChange: (List<QuickMenuAction>) -> Unit,
    hiddenGroups: Set<ToolbarGroup>,
    onHiddenGroupsChange: (Set<ToolbarGroup>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pinned by RailSettings.pinnedState(context)
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_title)) },
        text = {
            Column(Modifier.heightIn(max = FernSize.dialogMaxHeight).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.layout_rail), style = MaterialTheme.typography.titleSmall)
                ReorderableList(pinned, { stringResource(toolName(it)) }, { railIcon(state, it) }) { RailSettings.setPinned(context, it) }
                FlowRow {
                    for (tool in RAIL_EXTRA_TOOLS - pinned.toSet()) {
                        AssistChip(onClick = { RailSettings.setPinned(context, pinned + tool) }, label = { Text(stringResource(toolName(tool))) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                Text(stringResource(R.string.quick_menu), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FernScale.s16))
                ReorderableList(quickMenu, { stringResource(it.label) }, { it.icon(state) }, minimum = 1, onChange = onQuickMenuChange)
                if (quickMenu.size < QUICK_MENU_MAX_ACTIONS) FlowRow {
                    for (action in QuickMenuAction.entries - quickMenu.toSet()) {
                        AssistChip(onClick = { onQuickMenuChange(quickMenu + action) }, label = { Text(stringResource(action.label)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                Text(stringResource(R.string.layout_toolbar), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FernScale.s16))
                FlowRow {
                    for (group in ToolbarGroup.entries) {
                        FilterChip(selected = group !in hiddenGroups, onClick = { onHiddenGroupsChange(if (group in hiddenGroups) hiddenGroups - group else hiddenGroups + group) },
                            label = { Text(stringResource(group.label)) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
            }
        },
    )
}

/**
 * Rows that reorder by dragging their handle and leave with their remove button; at least
 * [minimum] stay.
 */
@Composable
fun <T> ReorderableList(items: List<T>, label: @Composable (T) -> String, icon: @Composable (T) -> ImageVector, minimum: Int = 0, onChange: (List<T>) -> Unit) {
    val rowPx = with(LocalDensity.current) { FernSize.touchTarget.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var order by remember(items) { mutableStateOf(items) }
    val moveUp = stringResource(R.string.layout_move_up)
    val moveDown = stringResource(R.string.layout_move_down)
    for ((index, item) in order.withIndex()) {
        val name = label(item)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FernSize.touchTarget)
                .offset { IntOffset(0, if (dragging == index) dragOffset.roundToInt() else 0) }
                // Without dragging (TalkBack, Switch Access): move up and down as actions.
                .semantics {
                    customActions = listOfNotNull(
                        if (index > 0) CustomAccessibilityAction(moveUp) { onChange(order.toMutableList().apply { add(index - 1, removeAt(index)) }); true } else null,
                        if (index < order.lastIndex) CustomAccessibilityAction(moveDown) { onChange(order.toMutableList().apply { add(index + 1, removeAt(index)) }); true } else null,
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.layout_drag, name),
                modifier = Modifier
                    .padding(FernScale.s12)
                    .pointerInput(order) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = index
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                dragging = null
                                dragOffset = 0f
                                onChange(order)
                            },
                            onDragCancel = {
                                dragging = null
                                order = items
                            },
                        ) { change, amount ->
                            change.consume()
                            val from = dragging ?: return@detectDragGestures
                            dragOffset += amount.y
                            // Past half a row, the item swaps with its neighbour.
                            val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                            if (to != from) {
                                order = order.toMutableList().apply { add(to, removeAt(from)) }
                                dragOffset -= (to - from) * rowPx
                                dragging = to
                            }
                        }
                    },
            )
            Icon(icon(item), contentDescription = null)
            Text(name, modifier = Modifier.weight(1f).padding(start = FernScale.s12))
            IconButton(onClick = { onChange(order - item) }, enabled = order.size > minimum) {
                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = stringResource(R.string.layout_remove, name))
            }
        }
    }
}
