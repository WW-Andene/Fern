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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The layer stack, listed top layer first like every paint app. Tap a name to make it the
 * active layer (new strokes go there); per layer: show/hide, lock, move up/down, rename,
 * delete. The active layer also gets an opacity slider and merge-down.
 */
@Composable
fun LayersDialog(state: CanvasState, onDismiss: () -> Unit) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Layers") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 384.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                for (layer in state.layers.reversed()) {
                    val active = layer.id == state.activeLayerId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { state.updateLayer(layer.id) { it.copy(visible = !it.visible) } }) {
                            Icon(
                                if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (layer.visible) "Hide layer" else "Show layer",
                            )
                        }
                        IconButton(onClick = { state.updateLayer(layer.id) { it.copy(locked = !it.locked) } }) {
                            Icon(
                                if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = if (layer.locked) "Unlock layer" else "Lock layer",
                            )
                        }
                        if (renamingId == layer.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                if (renameText.isNotBlank()) state.updateLayer(layer.id) { it.copy(name = renameText.trim()) }
                                renamingId = null
                            }) {
                                Icon(Icons.Filled.Check, contentDescription = "Confirm rename")
                            }
                        } else {
                            Text(
                                text = layer.name,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { state.activeLayerId = layer.id }
                                    .padding(vertical = 12.dp),
                            )
                            IconButton(onClick = {
                                renamingId = layer.id
                                renameText = layer.name
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename layer")
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { state.moveLayer(layer.id, up = true) }, enabled = layer != state.layers.last()) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move layer up")
                        }
                        IconButton(onClick = { state.moveLayer(layer.id, up = false) }, enabled = layer != state.layers.first()) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move layer down")
                        }
                        IconButton(onClick = { state.mergeLayerDown(layer.id) }, enabled = layer != state.layers.first()) {
                            Icon(Icons.Filled.MergeType, contentDescription = "Merge into layer below")
                        }
                        IconButton(onClick = { state.deleteLayer(layer.id) }, enabled = state.layers.size > 1) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete layer and its strokes")
                        }
                    }
                    if (active) {
                        Slider(
                            value = layer.opacity,
                            onValueChange = { value -> state.updateLayer(layer.id) { it.copy(opacity = value) } },
                            valueRange = 0f..1f,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.addLayer() }) { Text("New layer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
