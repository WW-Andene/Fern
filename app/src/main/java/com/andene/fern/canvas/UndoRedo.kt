package com.andene.fern.canvas

import com.andene.fern.FernIcons
import com.andene.fern.FernScale
import com.andene.fern.FernSize
import com.andene.fern.FernSlider
import com.andene.fern.FernSpace
import com.andene.fern.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * Undo and redo in thumb reach (design roadmap D79): on a phone held upright, with the toolbar at
 * the top, they sit at the bottom on the free hand's side instead of in the top bar. A long press
 * on undo opens the history scrubber: a slider over every step, undone and redone as it's dragged,
 * so going back ten steps is one drag rather than ten taps. Everything stays redoable until a new
 * action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UndoRedoControls(
    state: CanvasState,
    modifier: Modifier = Modifier,
    /** On its own surface (floating over the canvas), or bare inside a bar that has one. */
    framed: Boolean = true,
) {
    var scrubbing by remember { mutableStateOf(false) }
    val buttons: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val undoLabel = stringResource(R.string.action_undo)
            Box(
                Modifier
                    .size(FernSize.touchTarget)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = false, radius = FernScale.s24),
                        role = Role.Button,
                        onClickLabel = undoLabel,
                        onLongClickLabel = stringResource(R.string.history_scrub),
                        onLongClick = { scrubbing = true },
                        onClick = { state.undo() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(FernIcons.Undo, contentDescription = undoLabel)
            }
            IconButton(onClick = { state.redo() }) {
                Icon(FernIcons.Redo, contentDescription = stringResource(R.string.action_redo))
            }
        }
    }
    Box(modifier) {
        if (framed) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = FernScale.s4, shadowElevation = FernScale.s4) { buttons() }
        } else {
            buttons()
        }
        if (scrubbing) {
            Popup(popupPositionProvider = AboveOrBelow, onDismissRequest = { scrubbing = false }, properties = PopupProperties(focusable = true)) {
                HistoryScrubber(state, onDone = { scrubbing = false })
            }
        }
    }
}

/** Centred on what it opens from, above it when there's room, else below; kept inside the window. */
private object AboveOrBelow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom)
    }
}

/**
 * The history as a slider (design roadmap D79): from the start of the history to its newest step,
 * the thumb where the drawing is now. Dragging undoes or redoes to follow it.
 */
@Composable
private fun HistoryScrubber(state: CanvasState, onDone: () -> Unit) {
    // The history's length when the scrubber opened: scrubbing moves within it and adds no steps.
    val total = remember { state.historyDepth + state.redoDepth }
    var position by remember { mutableIntStateOf(state.historyDepth) }
    Surface(
        modifier = Modifier.padding(vertical = FernSpace.sm),
        shape = MaterialTheme.shapes.large,
        tonalElevation = FernScale.s4,
        shadowElevation = FernScale.s4,
    ) {
        Column(Modifier.padding(horizontal = FernSpace.md, vertical = FernSpace.xs)) {
            Text(pluralStringResource(R.plurals.history_steps_back, total - position, total - position), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FernSlider(
                    value = position.toFloat(),
                    onValueChange = { value ->
                        position = value.roundToInt()
                        state.scrubHistoryTo(position)
                    },
                    valueRange = 0f..total.coerceAtLeast(1).toFloat(),
                    steps = (total - 1).coerceAtLeast(0),
                    modifier = Modifier.width(FernScale.s192),
                )
                IconButton(onClick = onDone) {
                    Icon(FernIcons.Check, contentDescription = stringResource(R.string.action_done))
                }
            }
        }
    }
}
