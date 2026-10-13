package com.andene.fern.canvas

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.andene.fern.FernHaptics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Animation
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
import com.andene.fern.FernMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.pow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.andene.fern.FernScale
import androidx.compose.ui.text.style.TextOverflow
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
val RAIL_EXTRA_TOOLS = listOf(Tool.FILL, Tool.TEXT, Tool.EYEDROPPER, Tool.BEZIER, Tool.MEASURE, Tool.CONNECT, Tool.BONE, Tool.RIG)

/** Pinned before the user changes anything. */
val DEFAULT_PINNED_TOOLS = listOf(Tool.FILL, Tool.TEXT, Tool.EYEDROPPER)

private const val KEY_PINNED_TOOLS = "rail_pinned_tools"

/** Reading and saving the pinned tools. */
object RailSettings {
    private var loaded: MutableState<List<Tool>>? = null

    /** The pinned tools as state, so the rail follows a change made elsewhere (a workspace switch, design roadmap D11). */
    fun pinnedState(context: Context): MutableState<List<Tool>> = loaded ?: mutableStateOf(pinned(context)).also { loaded = it }

    fun pinned(context: Context): List<Tool> {
        val stored = AppSettings.prefs(context).getString(KEY_PINNED_TOOLS, null) ?: return DEFAULT_PINNED_TOOLS
        return stored.split(',').mapNotNull { name -> RAIL_EXTRA_TOOLS.firstOrNull { it.name == name } }
    }

    fun setPinned(context: Context, tools: List<Tool>) {
        AppSettings.prefs(context).edit().putString(KEY_PINNED_TOOLS, tools.joinToString(",") { it.name }).apply()
        loaded?.value = tools
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
    Tool.RIG -> Icons.Filled.Animation
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
    val pinned by RailSettings.pinnedState(context)
    var showMore by remember { mutableStateOf(false) }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, tonalElevation = FernSpace.xs, shadowElevation = FernSpace.xs) {
        BoxWithConstraints {
            // Labelled buttons are wider and taller (design roadmap D95), so fewer fit.
            val slot = if (iconLabelsShown()) FernScale.s64 else FernSize.touchTarget
            val room = ((if (vertical) maxHeight else maxWidth) / slot).toInt()
            // One place is kept for "More tools".
            val shownPinned = pinned.take((room - RAIL_TOOLS.size - 1).coerceAtLeast(0))
            val content: @Composable () -> Unit = {
                for (tool in RAIL_TOOLS + shownPinned) RailButton(state, tool)
                Box {
                    LabelledIconButton(onClick = { showMore = true }, icon = Icons.Filled.MoreHoriz, label = stringResource(R.string.rail_more_tools))
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        for (tool in RAIL_EXTRA_TOOLS) {
                            val isPinned = tool in pinned
                            FernMenuItem(
                                text = { Text(stringResource(toolName(tool))) },
                                leadingIcon = { Icon(railIcon(state, tool), contentDescription = null) },
                                onClick = {
                                    state.activeTool = tool
                                    showMore = false
                                    UsageHints.toolReachedIndirectly(context, tool)
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        RailSettings.setPinned(context, if (isPinned) pinned - tool else pinned + tool)
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

/**
 * One tool on the rail, highlighted while it's the active tool. A tap switches to it; holding it
 * uses it only while held, then the previous tool comes back (design roadmap D17) - e.g. hold
 * the eraser with one hand and erase with the other.
 */
@Composable
private fun RailButton(state: CanvasState, tool: Tool) {
    val active = state.activeTool == tool
    val view = LocalView.current
    val label = stringResource(toolName(tool))
    val labelled = iconLabelsShown()
    Box(
        modifier = Modifier
            .size(if (labelled) FernScale.s64 else FernSize.touchTarget)
            .clip(if (labelled) MaterialTheme.shapes.large else CircleShape)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .toolSelected(active)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    state.activeTool = tool
                    true
                }
            }
            .pointerInput(tool) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val adjustable = tool == Tool.PEN || tool == Tool.ERASER
                    var adjusting = false
                    // Up within the hold time (a tap), or a drag (adjusting); null when held still.
                    val released = withTimeoutOrNull(HOLD_TO_SWITCH_MILLIS) {
                        var ended: Boolean? = null
                        while (ended == null) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: continue
                            if (!change.pressed) {
                                ended = true
                            } else if (adjustable && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                adjusting = true
                                ended = true
                            }
                        }
                        ended
                    }
                    if (adjusting) {
                        // Dragging on the pen or eraser sets its size (up and down) or opacity (sideways) (design roadmap D19).
                        state.activeTool = tool
                        val startWidth = state.activeWidthWorld
                        val startOpacity = state.activeOpacity
                        RailAdjust.active = true
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val d = change.position - down.position
                            if (abs(d.y) >= abs(d.x)) {
                                state.activeWidthWorld = snapToPresetSize(startWidth * 2.0.pow(-d.y / ADJUST_DOUBLING_PX))
                            } else {
                                state.activeOpacity = (startOpacity + d.x / ADJUST_OPACITY_PX).coerceIn(MIN_OPACITY.toDouble(), 1.0)
                            }
                            change.consume()
                        }
                        RailAdjust.active = false
                    } else if (released != null) {
                        state.activeTool = tool
                    } else {
                        val previous = state.activeTool
                        state.activeTool = tool
                        FernHaptics.press(view)
                        waitForUpOrCancellation()
                        state.activeTool = previous
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else LocalContentColor.current
        if (labelled) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(railIcon(state, tool), contentDescription = null, tint = tint)
                Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {})
            }
        } else {
            Icon(railIcon(state, tool), contentDescription = null, tint = tint)
        }
    }
}

// Held this long, a rail tool is borrowed instead of switched to (Android's long-press time).
private const val HOLD_TO_SWITCH_MILLIS = 500L

// Dragging this far up doubles the brush; this far sideways goes from clear to solid.
private const val ADJUST_DOUBLING_PX = 96.0
private const val ADJUST_OPACITY_PX = 384.0

/** Brush sizes a drag settles on when it passes near them (design roadmap D19). */
val PRESET_SIZES = listOf(1.0, 2.0, 4.0, 8.0, 12.0, 16.0, 24.0, 32.0)

/** [width] snapped to a preset within 8 %, kept within the brush's range. */
fun snapToPresetSize(width: Double): Double {
    val clamped = width.coerceIn(MIN_WIDTH_SCREEN.toDouble(), MAX_WIDTH_SCREEN.toDouble())
    return PRESET_SIZES.firstOrNull { abs(it - clamped) <= it * 0.08 } ?: clamped
}

/** Whether a rail drag is adjusting the brush, so the canvas shows its size ring. */
object RailAdjust {
    var active by mutableStateOf(false)
}
