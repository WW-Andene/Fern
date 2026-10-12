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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The exposure sheet (roadmap item 88): one row per animation frame with how many playback
 * ticks it's held for, and - / + to change it, and its mouth shape for lip-sync (tap to cycle).
 * Tapping a row's name shows that frame. A frame held for 2 is "on twos" at the document's
 * speed. Above the rows: the soundtrack (choose, remove) and an automatic lip-sync pass from it.
 */
@Composable
fun XSheetDialog(state: CanvasState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    val pickSoundtrack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val extension = context.contentResolver.getType(uri)?.substringAfter('/') ?: "audio"
            val name = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { CanvasStorage.importSoundtrack(context, it, extension, replacing = state.soundtrack) }
            }
            if (name != null) state.setSoundtrackFile(name)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.xsheet_title)) },
        text = {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickSoundtrack.launch(arrayOf("audio/*")) }) {
                    Text(stringResource(if (state.soundtrack == null) R.string.xsheet_add_soundtrack else R.string.xsheet_replace_soundtrack))
                }
                val soundtrack = state.soundtrack
                if (soundtrack != null) {
                    TextButton(onClick = {
                        CanvasStorage.deleteSoundtrack(context, soundtrack)
                        state.setSoundtrackFile(null)
                    }) { Text(stringResource(R.string.xsheet_remove_soundtrack)) }
                    TextButton(enabled = !working, onClick = {
                        working = true
                        scope.launch {
                            val starts = (0..state.animFrameCount).map { state.frameStartMillis(it) }
                            val levels = withContext(Dispatchers.Default) {
                                runCatching { LipSync.levels(CanvasStorage.soundtrackFile(context, soundtrack), starts) }.getOrNull()
                            }
                            if (levels != null) state.setMouths(LipSync.mouthsFor(levels))
                            working = false
                        }
                    }) { Text(stringResource(R.string.xsheet_auto_lipsync)) }
                }
            }
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
                        // Mouth chart: cycles through the shapes, then back to none.
                        val mouth = state.frameMouths[frame]
                        TextButton(onClick = {
                            val next = if (mouth == null) MouthShape.entries.first() else MouthShape.entries.getOrNull(mouth.ordinal + 1)
                            state.setMouth(frame, next)
                        }) {
                            Text(mouth?.label ?: "·", modifier = Modifier.semantics { contentDescription = context.getString(R.string.xsheet_mouth, frame + 1) })
                        }
                    }
                }
            }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
