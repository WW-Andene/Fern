package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernSwitch
import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlin.math.roundToInt

/**
 * Joint limits for one bone (roadmap item 85): whether its joint is limited, and the range of
 * angles, in degrees relative to its parent, it may turn through. [current] (degrees) is the
 * joint's angle in this pose, shown for reference and used as the starting range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JointLimitsDialog(bone: Bone, current: Double, onApply: (min: Double?, max: Double?) -> Unit, onDismiss: () -> Unit) {
    val hadLimits = bone.minTurn != null && bone.maxTurn != null
    var limited by remember { mutableStateOf(hadLimits) }
    var range by remember {
        mutableStateOf(
            if (hadLimits) Math.toDegrees(bone.minTurn!!).toFloat()..Math.toDegrees(bone.maxTurn!!).toFloat()
            else (current - 45).toFloat().coerceAtLeast(-180f)..(current + 45).toFloat().coerceAtMost(180f),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.joint_limits_title)) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.joint_limits_enable), modifier = Modifier.weight(1f))
                    FernSwitch(checked = limited, onCheckedChange = { limited = it })
                }
                RangeSlider(value = range, onValueChange = { range = it }, valueRange = -180f..180f, enabled = limited)
                Text(
                    stringResource(R.string.joint_limits_range, range.start.roundToInt(), range.endInclusive.roundToInt(), current.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = FernScale.s8),
                )
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                if (limited) onApply(Math.toRadians(range.start.toDouble()), Math.toRadians(range.endInclusive.toDouble())) else onApply(null, null)
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
