package com.andene.fern.canvas

import com.andene.fern.FernScale

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.onClick
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.SwapHoriz
import com.andene.fern.FernHaptics
import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An action the quick menu's ring can hold (roadmap item 133: the user picks which, see
 * [LayoutEditor]); its icon is the toolbar's for the same thing (one icon per
 * meaning). Stored by name, so reordering this list never changes a saved choice; the ring
 * shows the chosen ones in this order.
 */
enum class QuickMenuAction(@StringRes val label: Int, val icon: (CanvasState) -> ImageVector, val run: (CanvasState) -> Unit) {
    PEN(R.string.pen_title, { penTypeIcon(it.activePenType) }, { it.activeTool = Tool.PEN }),
    ERASER(R.string.tb_eraser, { Icons.AutoMirrored.Filled.Backspace }, { it.activeTool = Tool.ERASER }),
    SELECT(R.string.action_select, { Icons.Filled.SelectAll }, { it.activeTool = Tool.SELECT }),
    SHAPES(R.string.quick_shapes, { shapeKindIcon(it.activeShapeKind) }, { it.activeTool = Tool.SHAPE }),
    REDO(R.string.action_redo, { Icons.AutoMirrored.Filled.Redo }, { it.redo() }),
    UNDO(R.string.action_undo, { Icons.AutoMirrored.Filled.Undo }, { it.undo() }),
    TEXT(R.string.text_label, { Icons.Filled.TextFields }, { it.activeTool = Tool.TEXT }),
    FILL(R.string.tb_fill, { Icons.Filled.FormatColorFill }, { it.activeTool = Tool.FILL }),
    EYEDROPPER(R.string.tb_eyedropper, { Icons.Filled.Colorize }, { it.activeTool = Tool.EYEDROPPER }),
    RULER(R.string.tb_ruler, { Icons.Filled.Straighten }, { it.toggleRuler() }),
    ZOOM_TO_FIT(R.string.tb_fit, { Icons.Filled.CenterFocusStrong }, { it.zoomToFit() }),
    DUPLICATE(R.string.tb_duplicate, { Icons.Filled.ContentCopy }, { it.duplicateSelection() }),
    DELETE(R.string.tb_delete, { Icons.Filled.Delete }, { it.deleteSelection() }),
    APPLY_COLOUR(R.string.tb_apply_colour, { Icons.Filled.FormatPaint }, { it.recolorSelection() }),
    // Any tool, and brush size, can sit on the ring too (design roadmap D21).
    BEZIER(R.string.tool_name_bezier, { Icons.Filled.Polyline }, { it.activeTool = Tool.BEZIER }),
    MEASURE(R.string.tool_name_measure, { Icons.Filled.SquareFoot }, { it.activeTool = Tool.MEASURE }),
    CONNECT(R.string.tool_name_connect, { Icons.Filled.AccountTree }, { it.activeTool = Tool.CONNECT }),
    BONE(R.string.tool_name_bone, { Icons.Filled.AccessibilityNew }, { it.activeTool = Tool.BONE }),
    SWAP_ERASER(R.string.gesture_action_toggle_eraser, { Icons.Filled.SwapHoriz }, { it.toggleEraser() }),
    BIGGER(R.string.shortcut_size_up, { Icons.Filled.AddCircleOutline }, { it.activeWidthWorld = snapToPresetSize(it.activeWidthWorld * 1.25) }),
    SMALLER(R.string.shortcut_size_down, { Icons.Filled.RemoveCircleOutline }, { it.activeWidthWorld = snapToPresetSize(it.activeWidthWorld / 1.25) }),
}

/** The ring before the user changes it: tools first, then undo/redo at the bottom where the thumb rests. */
val DEFAULT_QUICK_MENU = listOf(
    QuickMenuAction.PEN, QuickMenuAction.ERASER, QuickMenuAction.SELECT, QuickMenuAction.SHAPES,
    QuickMenuAction.REDO, QuickMenuAction.UNDO, QuickMenuAction.TEXT, QuickMenuAction.FILL,
)

/** The most the ring holds: twelve 48 dp buttons fit around its 96 dp radius without overlapping. */
const val QUICK_MENU_MAX_ACTIONS = 12

// Ring radius and item size; both on the §7 scale.
private const val RING_RADIUS_DP = 96
private const val ITEM_SIZE_DP = 48

/**
 * The floating button that opens the quick menu; sits at the canvas edge away from the drawing
 * hand. A tap opens the ring; a flick from it toward an action runs that action on release
 * without the ring ever showing (design roadmap D21), with a tick as each action comes under
 * the thumb - so it works without looking.
 */
@Composable
fun QuickMenuButton(onOpen: () -> Unit, modifier: Modifier = Modifier, state: CanvasState? = null, actions: List<QuickMenuAction> = DEFAULT_QUICK_MENU) {
    val label = stringResource(R.string.quick_menu)
    val view = LocalView.current
    Surface(
        modifier = modifier
            .size(ITEM_SIZE_DP.dp)
            .semantics {
                contentDescription = label
                onClick(label) {
                    onOpen()
                    true
                }
            }
            .pointerInput(actions) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var aimed: Int? = null
                    val flickPx = FLICK_DP.dp.toPx()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        val d = change.position - down.position
                        if (!change.pressed) break
                        val now = if (d.getDistance() >= flickPx && actions.isNotEmpty()) flickIndex(d.x, d.y, actions.size) else null
                        if (now != aimed && now != null) FernHaptics.tick(view)
                        aimed = now
                        change.consume()
                    }
                    val chosen = aimed
                    if (chosen == null) onOpen() else state?.let { Macros.perform(it, actions[chosen]) }
                }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = FernScale.s4,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DonutLarge, contentDescription = null)
        }
    }
}

/** Which of [count] ring positions (the ring's layout: the first at the top, then clockwise) the direction (dx, dy) points at. */
fun flickIndex(dx: Float, dy: Float, count: Int): Int {
    val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()) + PI / 2 // 0 at the top
    val turn = ((angle / (2 * PI)) % 1.0 + 1.0) % 1.0
    return kotlin.math.round(turn * count).toInt() % count
}

// A flick this far from the button picks an action.
private const val FLICK_DP = 48

/**
 * Radial quick menu (Procreate's QuickMenu, Krita's pop-up palette): the most-used tools and
 * undo/redo in a ring at the centre of the screen. Tapping an action runs it and closes the
 * menu; tapping the centre button or anywhere outside the ring closes it without doing anything.
 * Covers the canvas while open, so the dismissing tap can't also draw.
 */
@Composable
fun QuickMenuOverlay(state: CanvasState, onDismiss: () -> Unit, actions: List<QuickMenuAction> = DEFAULT_QUICK_MENU) {
    val closeLabel = stringResource(R.string.quick_menu_close)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            // No ripple on the scrim: the dismissal itself is the feedback.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        for ((index, action) in actions.withIndex()) {
            val label = stringResource(action.label)
            val angle = -PI / 2 + 2 * PI * index / actions.size
            Surface(
                modifier = Modifier
                    .offset((cos(angle) * RING_RADIUS_DP).dp, (sin(angle) * RING_RADIUS_DP).dp)
                    .size(ITEM_SIZE_DP.dp)
                    .semantics { contentDescription = label }
                    .clickable {
                        Macros.perform(state, action)
                        onDismiss()
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = FernScale.s4,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(action.icon(state), contentDescription = null) }
            }
        }
        Surface(
            modifier = Modifier
                .size(ITEM_SIZE_DP.dp)
                .semantics { contentDescription = closeLabel }
                .clickable(onClick = onDismiss),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = null) }
        }
    }
}
