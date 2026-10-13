package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * Procedural motion for the selected bone (roadmap item 135): a sine sway or a smooth wiggle of
 * its angle across every frame, added to the poses already there - set the swing and how many
 * frames one takes, then Apply (undoable). Needs a selected bone.
 */
@Composable
fun SwayDialog(state: CanvasState, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(SwayKind.SINE) }
    var degrees by remember { mutableFloatStateOf(15f) }
    var period by remember { mutableFloatStateOf(8f) }
    val boneId = state.selectedBoneId
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sway_title)) },
        text = {
            Column {
                if (boneId == null) {
                    Text(stringResource(R.string.sway_no_bone))
                    return@Column
                }
                Row {
                    for ((option, label) in listOf(SwayKind.SINE to R.string.sway_sine, SwayKind.WIGGLE to R.string.sway_wiggle)) {
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                Text(stringResource(R.string.sway_amplitude, degrees.toInt()))
                FernSlider(value = degrees, onValueChange = { degrees = it }, valueRange = 1f..90f)
                Text(stringResource(R.string.sway_period, period.toInt()))
                FernSlider(value = period, onValueChange = { period = it }, valueRange = 2f..48f)
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                val id = boneId ?: return@FernTextButton
                val amplitude = Math.toRadians(degrees.toDouble())
                val chosen = kind
                val frames = period.toInt().toDouble()
                state.swayBone(id) { frame -> proceduralSway(chosen, frame, amplitude, frames, seed = id.hashCode()) }
                onDismiss()
            }, enabled = boneId != null) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
