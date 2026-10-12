package com.andene.fern.canvas

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andene.fern.FernSize
import com.andene.fern.FernSpace
import com.andene.fern.R

/** The tools always on the rail (design roadmap D8), in their fixed order. */
// Four, so that on a 360 dp phone two pinned tools and "More tools" still fit beside them.
val RAIL_TOOLS = listOf(Tool.PEN, Tool.ERASER, Tool.SELECT, Tool.SHAPE)

/** The other tools; each can be pinned to the rail after the fixed ones. */
val RAIL_EXTRA_TOOLS = listOf(Tool.FILL, Tool.TEXT, Tool.EYEDROPPER, Tool.BEZIER, Tool.MEASURE, Tool.CONNECT, Tool.BONE)

/** Pinned before the user changes anything. */
val DEFAULT_PINNED_TOOLS = listOf(Tool.FILL, Tool.TEXT, Tool.EYEDROPPER)

private const val KEY_PINNED_TOOLS = "rail_pinned_tools"

/** Reading and saving the pinned tools. */
object RailSettings {
    fun pinned(context: Context): List<Tool> {
        val stored = AppSettings.prefs(context).getString(KEY_PINNED_TOOLS, null) ?: return DEFAULT_PINNED_TOOLS
        return stored.split(',').mapNotNull { name -> RAIL_EXTRA_TOOLS.firstOrNull { it.name == name } }
    }

    fun setPinned(context: Context, tools: List<Tool>) {
        AppSettings.prefs(context).edit().putString(KEY_PINNED_TOOLS, tools.joinToString(",") { it.name }).apply()
    }
}

/** The icon of [tool] on the rail; the pen and shape show their current kind. */
@Composable
internal fun railIcon(state: CanvasState, tool: Tool): ImageVector = when (tool) {
    Tool.PEN -> penTypeIcon(state.activePenType)
    Tool.ERASER -> Icons.AutoMirrored.Filled.Backspace
    Tool.SELECT -> Icons.Filled.SelectAll
    Tool.SHAPE -> shapeKindIcon(state.activeShapeKind)
    Tool.FILL -> Icons.Filled.FormatColorFill
    Tool.TEXT -> Icons.Filled.TextFields
    Tool.EYEDROPPER -> Icons.Filled.Colorize
    Tool.BEZIER -> Icons.Filled.Polyline
    Tool.MEASURE -> Icons.Filled.SquareFoot
    Tool.CONNECT -> Icons.Filled.AccountTree
    Tool.BONE -> Icons.Filled.AccessibilityNew
    Tool.RULER -> Icons.Filled.SquareFoot
}

/**
 * The tool rail (design roadmap D8): the main tools in a fixed order that never scrolls, then
 * the tools the user pinned, then "More tools" with the rest - so a tool is always in the same
 * place and the hand learns it. Horizontal along the bottom of a phone held upright, vertical
 * along the side otherwise. Pinned tools that don't fit move into "More tools".
 */
@Composable
fun ToolRail(state: CanvasState, vertical: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pinned by remember { mutableStateOf(RailSettings.pinned(context)) }
    var showMore by remember { mutableStateOf(false) }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, tonalElevation = FernSpace.xs, shadowElevation = FernSpace.xs) {
        BoxWithConstraints {
            val room = ((if (vertical) maxHeight else maxWidth) / FernSize.touchTarget).toInt()
            // One place is kept for "More tools".
            val shownPinned = pinned.take((room - RAIL_TOOLS.size - 1).coerceAtLeast(0))
            val content: @Composable () -> Unit = {
                for (tool in RAIL_TOOLS + shownPinned) RailButton(state, tool)
                Box {
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.rail_more_tools))
                    }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        for (tool in RAIL_EXTRA_TOOLS) {
                            val isPinned = tool in pinned
                            DropdownMenuItem(
                                text = { Text(stringResource(toolName(tool))) },
                                leadingIcon = { Icon(railIcon(state, tool), contentDescription = null) },
                                onClick = {
                                    state.activeTool = tool
                                    showMore = false
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        pinned = if (isPinned) pinned - tool else pinned + tool
                                        RailSettings.setPinned(context, pinned)
                                    }) {
                                        Icon(
                                            if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = stringResource(if (isPinned) R.string.rail_unpin else R.string.rail_pin, stringResource(toolName(tool))),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (vertical) Column(verticalArrangement = Arrangement.Center) { content() } else Row(horizontalArrangement = Arrangement.Center) { content() }
        }
    }
}

/** One tool on the rail, highlighted while it's the active tool. */
@Composable
private fun RailButton(state: CanvasState, tool: Tool) {
    val active = state.activeTool == tool
    IconButton(
        onClick = { state.activeTool = tool },
        modifier = Modifier.toolSelected(active),
        colors = if (active) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors(),
    ) {
        Icon(railIcon(state, tool), contentDescription = stringResource(toolName(tool)))
    }
}
