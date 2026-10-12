package com.andene.fern.canvas

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

/** One action on the quick menu's ring; its icon is the toolbar's for the same thing (one icon per meaning). */
private data class QuickAction(val label: String, val icon: (CanvasState) -> ImageVector, val run: (CanvasState) -> Unit)

// Clockwise from the top. Tools first, then undo/redo at the bottom where the thumb rests.
private val QUICK_ACTIONS = listOf(
    QuickAction("Pen", { penTypeIcon(it.activePenType) }) { it.activeTool = Tool.PEN },
    QuickAction("Eraser", { Icons.AutoMirrored.Filled.Backspace }) { it.activeTool = Tool.ERASER },
    QuickAction("Select", { Icons.Filled.SelectAll }) { it.activeTool = Tool.SELECT },
    QuickAction("Shapes", { shapeKindIcon(it.activeShapeKind) }) { it.activeTool = Tool.SHAPE },
    QuickAction("Redo", { Icons.AutoMirrored.Filled.Redo }) { it.redo() },
    QuickAction("Undo", { Icons.AutoMirrored.Filled.Undo }) { it.undo() },
    QuickAction("Text", { Icons.Filled.TextFields }) { it.activeTool = Tool.TEXT },
    QuickAction("Fill", { Icons.Filled.FormatColorFill }) { it.activeTool = Tool.FILL },
)

// Ring radius and item size; both on the §7 scale.
private const val RING_RADIUS_DP = 96
private const val ITEM_SIZE_DP = 48

/** The floating button that opens the quick menu; sits at the canvas edge away from the drawing hand. */
@Composable
fun QuickMenuButton(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(ITEM_SIZE_DP.dp)
            .semantics { contentDescription = "Quick menu" }
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
fun QuickMenuOverlay(state: CanvasState, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            // No ripple on the scrim: the dismissal itself is the feedback.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        for ((index, action) in QUICK_ACTIONS.withIndex()) {
            val angle = -PI / 2 + 2 * PI * index / QUICK_ACTIONS.size
            Surface(
                modifier = Modifier
                    .offset((cos(angle) * RING_RADIUS_DP).dp, (sin(angle) * RING_RADIUS_DP).dp)
                    .size(ITEM_SIZE_DP.dp)
                    .semantics { contentDescription = action.label }
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
                .semantics { contentDescription = "Close quick menu" }
                .clickable(onClick = onDismiss),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = null) }
        }
    }
}
