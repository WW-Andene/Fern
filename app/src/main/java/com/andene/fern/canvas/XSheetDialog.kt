package com.andene.fern.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * The exposure sheet (roadmap item 88): one row per animation frame with how many playback
 * ticks it's held for, and - / + to change it. Tapping a row's name shows that frame. A
 * frame held for 2 is "on twos" at the document's speed.
 */
@Composable
fun XSheetDialog(state: CanvasState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.xsheet_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 384.dp)) {
                items(state.animFrameCount) { frame ->
                    val hold = state.holdOf(frame)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.xsheet_frame, frame + 1),
                            fontWeight = if (frame == state.currentAnimFrame) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { state.goToAnimFrame(frame) }
                                .padding(vertical = 12.dp),
                        )
                        IconButton(onClick = { state.setHold(frame, hold - 1) }, enabled = hold > 1) {
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.xsheet_shorter, frame + 1))
                        }
                        Text(stringResource(R.string.xsheet_hold, hold), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { state.setHold(frame, hold + 1) }, enabled = hold < CanvasState.MAX_FRAME_HOLD) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.xsheet_longer, frame + 1))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
