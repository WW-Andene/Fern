package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlin.math.roundToInt

/** Picks a frame and a weight to blend the current frame's pose with (roadmap item 86). */
@Composable
fun BlendPoseDialog(state: CanvasState, onDismiss: () -> Unit) {
    val count = state.animFrameCount
    val current = state.currentAnimFrame
    var other by remember { mutableIntStateOf(if (current + 1 < count) current + 1 else maxOf(0, current - 1)) }
    var weight by remember { mutableFloatStateOf(0.5f) }
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blend_pose_title)) },
        text = {
            Column {
                Text(stringResource(R.string.blend_pose_frame, other + 1))
                FernSlider(
                    value = other.toFloat(),
                    onValueChange = { other = it.roundToInt() },
                    valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                    steps = (count - 2).coerceAtLeast(0),
                )
                Text(stringResource(R.string.blend_pose_weight, (weight * 100).roundToInt()))
                FernSlider(value = weight, onValueChange = { weight = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            FernTextButton(onClick = { state.blendPoseWith(other, weight.toDouble()); onDismiss() }, enabled = other != current) {
                Text(stringResource(R.string.blend_pose_apply))
            }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
