package com.andene.fern.canvas

import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The current document's voice notes: record a new one (the caller has already obtained the
 * microphone permission), play or delete existing ones. Recording closes the dialog and carries
 * on while the user draws ([AudioBar] shows it and stops it); playing closes it too and plays the
 * note on the canvas in step with the ink drawn during it ([onPlay]).
 */
@Composable
fun AudioNotesDialog(audio: AudioNotes, documentId: String, onPlay: (File) -> Unit, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf(audio.list(documentId)) }
    val recording = audio.recordingSince != null
    var failed by remember { mutableStateOf(false) }
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = FernScale.s384).verticalScroll(rememberScrollState())) {
                if (failed) Text(stringResource(R.string.audio_mic_failed))
                if (notes.isEmpty() && !recording) Text(stringResource(R.string.audio_empty))
                for (note in notes) {
                    // File names end in _<timestamp>.m4a.
                    val time = note.nameWithoutExtension.substringAfterLast('_').toLongOrNull()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(time?.let { format.format(Date(it)) } ?: note.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onPlay(note) }, enabled = !recording) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_play))
                        }
                        IconButton(onClick = {
                            note.delete()
                            notes = audio.list(documentId)
                        }, enabled = !recording) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.audio_delete))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (recording) {
                    audio.stop()
                    notes = audio.list(documentId)
                } else {
                    audio.stopPlayback()
                    failed = !audio.start(documentId)
                    if (!failed) onDismiss() // draw while recording
                }
            }) { Text(if (recording) stringResource(R.string.audio_stop_recording) else stringResource(R.string.audio_record)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
