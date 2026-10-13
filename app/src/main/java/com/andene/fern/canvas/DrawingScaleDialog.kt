package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import com.andene.fern.FernMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andene.fern.R

/**
 * The document's drawing scale (roadmap item 143). Pick a unit and either say how long one
 * canvas unit (a pixel at 100 % zoom) is in it, or - when a line has just been measured - how
 * long that line really is, which calibrates the scale from it, as Concepts does. "No scale"
 * goes back to measuring in canvas units. Saved with the document.
 */
@Composable
fun DrawingScaleDialog(state: CanvasState, onDismiss: () -> Unit) {
    val current = state.drawingScale
    // A measured straight line offers calibration; its length in canvas units.
    val measuredLine = state.measureTool.measurement?.takeIf { it.points.size == 2 && !it.closed && it.length > 0 }?.length
    var unit by remember { mutableStateOf(current?.unit ?: MeasureUnit.CENTIMETRE) }
    var showUnits by remember { mutableStateOf(false) }
    var value by remember {
        mutableStateOf(
            when {
                measuredLine != null -> formatMeasure(measuredLine * current.factor)
                current != null -> formatMeasure(current.unitsPerWorld)
                else -> "1"
            },
        )
    }
    val parsed = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scale_title)) },
        text = {
            Column {
                Text(
                    if (measuredLine != null) stringResource(R.string.scale_measured_line, formatMeasure(measuredLine))
                    else stringResource(R.string.scale_one_unit),
                )
                Row {
                    FernTextField(
                        value,
                        { value = it },
                        singleLine = true,
                        isError = parsed == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                    Box {
                        FernTextButton(onClick = { showUnits = true }) { Text(unit.symbol) }
                        DropdownMenu(expanded = showUnits, onDismissRequest = { showUnits = false }) {
                            for (option in MeasureUnit.entries) {
                                FernMenuItem(text = { Text(option.symbol) }, onClick = {
                                    unit = option
                                    showUnits = false
                                })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (current != null) FernTextButton(onClick = {
                    state.changeDrawingScale(null)
                    onDismiss()
                }) { Text(stringResource(R.string.scale_none)) }
                FernTextButton(onClick = {
                    val real = parsed ?: return@FernTextButton
                    state.changeDrawingScale(
                        if (measuredLine != null) DrawingScale.calibrated(measuredLine, real, unit) else DrawingScale(unit, real),
                    )
                    onDismiss()
                }, enabled = parsed != null) { Text(stringResource(R.string.action_apply)) }
            }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
