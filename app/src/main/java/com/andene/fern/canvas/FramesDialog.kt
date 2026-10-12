package com.andene.fern.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The document's frames (artboards). Tap a name to fit it on screen; export one as a PNG;
 * the field at the bottom adds a frame covering the current view. With frames present, PDF
 * export produces one page per frame.
 */
@Composable
fun FramesDialog(
    frames: List<Frame>,
    onJump: (Frame) -> Unit,
    onExport: (Frame) -> Unit,
    onDelete: (Frame) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newFrameName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frames") },
        text = {
            Column {
                Column(
                    modifier = Modifier
                        .heightIn(max = 384.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (frames.isEmpty()) {
                        Text("No frames yet. A frame marks out an area of the canvas to jump to and export as a page.")
                    }
                    for (frame in frames) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = frame.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onJump(frame) }
                                    .padding(vertical = 12.dp),
                            )
                            IconButton(onClick = { onExport(frame) }) {
                                Icon(Icons.Filled.IosShare, contentDescription = "Export frame as PNG")
                            }
                            IconButton(onClick = { onDelete(frame) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete frame")
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newFrameName,
                        onValueChange = { newFrameName = it },
                        label = { Text("New frame from current view") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (newFrameName.isNotBlank()) {
                            onCreate(newFrameName.trim())
                            newFrameName = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add frame")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
