package com.andene.fern.canvas

import com.andene.fern.FernPanel

import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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

    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pins_title)) },
        text = {
            Column {
                // Empty states say what the panel is for and how to fill it (design roadmap D28).
                if (pins.isEmpty()) Text(stringResource(R.string.pins_empty), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                for (pin in pins) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FernScale.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pin.name,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onJump(pin) },
                        )
                        IconButton(onClick = { onDelete(pin) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.pins_delete))
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newPinName,
                        onValueChange = { newPinName = it },
                        label = { Text(stringResource(R.string.pins_new_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (newPinName.isNotBlank()) {
                            onCreate(newPinName)
                            newPinName = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pins_add))
                    }
                }
            }
        },
    )
}
