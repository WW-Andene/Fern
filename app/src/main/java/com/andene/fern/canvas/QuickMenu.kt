package com.andene.fern.canvas

import androidx.annotation.StringRes
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
 * [QuickMenuCustomizeDialog]); its icon is the toolbar's for the same thing (one icon per
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

/** The floating button that opens the quick menu; sits at the canvas edge away from the drawing hand. */
@Composable
fun QuickMenuButton(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.quick_menu)
    Surface(
        modifier = modifier
            .size(ITEM_SIZE_DP.dp)
            .semantics { contentDescription = label }
            .clickable(onClick = onOpen),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DonutLarge, contentDescription = null)
        }
    }
}

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
                        action.run(state)
                        onDismiss()
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
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
