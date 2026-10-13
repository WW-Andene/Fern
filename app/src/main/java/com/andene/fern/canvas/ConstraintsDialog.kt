package com.andene.fern.canvas

import com.andene.fern.FernScale

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * Constraints for the selection (roadmap item 144): how it follows its frame when the frame is
 * resized, across and down - pinned to an edge, to both (stretching), to the centre, or scaled.
 * Apply sets them on every selected stroke (undoable).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConstraintsDialog(state: CanvasState, onDismiss: () -> Unit) {
    val current = state.selectionConstraints
    var horizontal by remember { mutableStateOf(current.horizontal) }
    var vertical by remember { mutableStateOf(current.vertical) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.constraints_title)) },
        text = {
            Column {
                Text(stringResource(R.string.constraints_horizontal))
                FlowRow {
                    for (pin in ConstraintPin.entries) {
                        FilterChip(selected = horizontal == pin, onClick = { horizontal = pin }, label = { Text(stringResource(pinLabel(pin, across = true))) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                Text(stringResource(R.string.constraints_vertical))
                FlowRow {
                    for (pin in ConstraintPin.entries) {
                        FilterChip(selected = vertical == pin, onClick = { vertical = pin }, label = { Text(stringResource(pinLabel(pin, across = false))) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.setSelectionConstraints(LayoutConstraints(horizontal, vertical))
                onDismiss()
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@StringRes
private fun pinLabel(pin: ConstraintPin, across: Boolean) = when (pin) {
    ConstraintPin.START -> if (across) R.string.constraint_left else R.string.constraint_top
    ConstraintPin.END -> if (across) R.string.constraint_right else R.string.constraint_bottom
    ConstraintPin.BOTH -> if (across) R.string.constraint_left_right else R.string.constraint_top_bottom
    ConstraintPin.CENTER -> R.string.constraint_centre
    ConstraintPin.SCALE -> R.string.constraint_scale
}
