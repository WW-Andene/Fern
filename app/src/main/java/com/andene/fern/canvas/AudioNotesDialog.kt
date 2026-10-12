package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The current document's voice notes: record a new one (the caller has already obtained the
 * microphone permission), play or delete existing ones. Recording and playback stop when the
 * dialog closes.
 */
@Composable
fun AudioNotesDialog(audio: AudioNotes, documentId: String, onDismiss: () -> Unit) {
    var notes by remember { mutableStateOf(audio.list(documentId)) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            audio.stop()
            audio.stopPlayback()
        }
    }
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice notes") },
        text = {
            Column(modifier = Modifier.heightIn(max = 384.dp).verticalScroll(rememberScrollState())) {
                if (failed) Text("The microphone couldn't be started.")
                if (notes.isEmpty() && !recording) Text("No voice notes for this document yet.")
                for (note in notes) {
                    // File names end in _<timestamp>.m4a.
                    val time = note.nameWithoutExtension.substringAfterLast('_').toLongOrNull()
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(time?.let { format.format(Date(it)) } ?: note.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            if (playing == note) {
                                audio.stopPlayback()
                                playing = null
                            } else {
                                audio.play(note) { playing = null }
                                playing = note
                            }
                        }, enabled = !recording) {
                            Icon(
                                if (playing == note) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (playing == note) "Stop" else "Play",
                            )
                        }
                        IconButton(onClick = {
                            if (playing == note) audio.stopPlayback()
                            note.delete()
                            notes = audio.list(documentId)
                        }, enabled = !recording) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete voice note")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (recording) {
                    audio.stop()
                    recording = false
                    notes = audio.list(documentId)
                } else {
                    playing = null
                    failed = !audio.start(documentId)
                    recording = !failed
                }
            }) { Text(if (recording) "Stop recording" else "Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
