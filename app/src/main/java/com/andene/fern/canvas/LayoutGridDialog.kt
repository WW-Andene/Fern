package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlin.math.roundToInt

/**
 * A frame's layout grid (roadmap item 142): how many columns and rows, the gutter between them
 * and the margin around them, in world units (canvas units at 100 % zoom). Apply lays it over the
 * frame; Remove takes it away. A count of 0 leaves that direction without a grid.
 */
@Composable
fun LayoutGridDialog(frame: Frame, onApply: (LayoutGrid?) -> Unit, onDismiss: () -> Unit) {
    val current = frame.layoutGrid
    var columns by remember { mutableFloatStateOf((current?.columns ?: DEFAULT_COLUMNS).toFloat()) }
    var rows by remember { mutableFloatStateOf((current?.rows ?: 0).toFloat()) }
    var gutter by remember { mutableFloatStateOf((current?.gutter ?: DEFAULT_GUTTER).toFloat()) }
    var margin by remember { mutableFloatStateOf((current?.margin ?: DEFAULT_MARGIN).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_grid_title, frame.name)) },
        text = {
            Column {
                Text(stringResource(R.string.layout_grid_columns, columns.roundToInt()))
                FernSlider(value = columns, onValueChange = { columns = it.roundToInt().toFloat() }, valueRange = 0f..MAX_COUNT)
                Text(stringResource(R.string.layout_grid_rows, rows.roundToInt()))
                FernSlider(value = rows, onValueChange = { rows = it.roundToInt().toFloat() }, valueRange = 0f..MAX_COUNT)
                Text(stringResource(R.string.layout_grid_gutter, gutter.roundToInt()))
                FernSlider(value = gutter, onValueChange = { gutter = it.roundToInt().toFloat() }, valueRange = 0f..MAX_SPACING)
                Text(stringResource(R.string.layout_grid_margin, margin.roundToInt()))
                FernSlider(value = margin, onValueChange = { margin = it.roundToInt().toFloat() }, valueRange = 0f..MAX_SPACING)
            }
        },
        confirmButton = {
            Row {
                if (current != null) FernTextButton(onClick = { onApply(null) }) { Text(stringResource(R.string.layout_grid_remove)) }
                FernTextButton(
                    onClick = { onApply(LayoutGrid(columns.roundToInt(), rows.roundToInt(), gutter.toDouble(), margin.toDouble())) },
                    enabled = columns >= 1f || rows >= 1f,
                ) { Text(stringResource(R.string.action_apply)) }
            }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val DEFAULT_COLUMNS = 12
private const val DEFAULT_GUTTER = 16.0
private const val DEFAULT_MARGIN = 32.0
private const val MAX_COUNT = 24f
private const val MAX_SPACING = 128f
