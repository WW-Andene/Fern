package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlin.math.roundToInt

/**
 * Contour (roadmap item 141): an outline drawn a set distance around the selection - outward,
 * or inward for closed shapes with a negative distance - with round, mitred or bevelled
 * corners. The distance is in screen pixels at the current zoom, so it reads as it looks.
 * Apply adds the contour as new selected outlines (undoable); the selection itself stays.
 */
@Composable
fun ContourDialog(state: CanvasState, onDismiss: () -> Unit) {
    var distance by remember { mutableFloatStateOf(16f) }
    var join by remember { mutableStateOf(ContourJoin.ROUND) }
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contour_title)) },
        text = {
            Column {
                Text(stringResource(R.string.contour_distance, distance.roundToInt()))
                FernSlider(value = distance, onValueChange = { distance = it.roundToInt().toFloat() }, valueRange = -64f..64f)
                Row {
                    for ((option, label) in listOf(
                        ContourJoin.ROUND to R.string.contour_round,
                        ContourJoin.MITRE to R.string.contour_mitre,
                        ContourJoin.BEVEL to R.string.contour_bevel,
                    )) {
                        FilterChip(selected = join == option, onClick = { join = option }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                state.contourSelection(distance.toDouble() / state.scale, join)
                onDismiss()
            }, enabled = distance != 0f) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
