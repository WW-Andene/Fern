package com.andene.fern.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
 * Lists every pin (named camera position) in the current document. Tapping a pin's name
 * jumps the camera there; the text field at the bottom creates a new pin at the camera's
 * current position/zoom.
 */
@Composable
fun PinsDialog(
    pins: List<PinMeta>,
    onJump: (PinMeta) -> Unit,
    onDelete: (PinMeta) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newPinName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pins") },
        text = {
            Column {
                for (pin in pins) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pin.name,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onJump(pin) },
                        )
                        IconButton(onClick = { onDelete(pin) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete pin")
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newPinName,
                        onValueChange = { newPinName = it },
                        label = { Text("New pin name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (newPinName.isNotBlank()) {
                            onCreate(newPinName)
                            newPinName = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add pin at current view")
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
