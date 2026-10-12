package com.andene.fern.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * A game object (roadmap item 155): its name, its number properties, and its behaviours with
 * their settings - add, change, remove. Follow path takes [path], a stroke selected along with
 * the object. Changes save as they're made. "Not an object" turns it back into a drawing.
 */
@Composable
fun GameObjectDialog(state: CanvasState, initial: GameObject, path: List<WorldPoint>?, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf(initial) }
    fun save(updated: GameObject) {
        current = updated
        state.updateObject(updated)
    }
    var newProperty by remember { mutableStateOf("") }
    var showBehaviours by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.object_title)) },
        text = {
            Column(Modifier.heightIn(max = 512.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(current.name, { save(current.copy(name = it)) }, label = { Text(stringResource(R.string.object_name)) }, singleLine = true)
                Text(stringResource(R.string.object_properties), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                for ((key, value) in current.properties) key(key) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(key, modifier = Modifier.weight(1f))
                        NumberField(value) { save(current.copy(properties = current.properties + (key to it))) }
                        IconButton(onClick = { save(current.copy(properties = current.properties - key)) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.object_remove_property))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newProperty, { newProperty = it.filter { c -> c.isLetterOrDigit() || c == '_' } }, label = { Text(stringResource(R.string.object_new_property)) }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        save(current.copy(properties = current.properties + (newProperty to 0.0)))
                        newProperty = ""
                    }, enabled = newProperty.isNotEmpty() && newProperty !in current.properties) { Text(stringResource(R.string.object_add)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.object_behaviours), style = MaterialTheme.typography.titleSmall)
                for ((index, behaviour) in current.behaviours.withIndex()) key(index, behaviour.kind) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(behaviour.kind.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { save(current.copy(behaviours = current.behaviours.filterIndexed { i, _ -> i != index })) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.object_remove_behaviour))
                        }
                    }
                    for (param in behaviour.kind.params) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(param.label), modifier = Modifier.weight(1f))
                            NumberField(behaviour.param(param.key)) { value ->
                                val updated = behaviour.copy(params = behaviour.params + (param.key to value))
                                save(current.copy(behaviours = current.behaviours.mapIndexed { i, b -> if (i == index) updated else b }))
                            }
                        }
                    }
                    if (behaviour.kind == BehaviourKind.FOLLOW_PATH) {
                        Text(
                            if (behaviour.path == null) stringResource(R.string.object_path_missing) else stringResource(R.string.object_path_points, behaviour.path.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (path != null) TextButton(onClick = {
                            save(current.copy(behaviours = current.behaviours.mapIndexed { i, b -> if (i == index) b.copy(path = path) else b }))
                        }) { Text(stringResource(R.string.object_use_path)) }
                    }
                }
                Box {
                    TextButton(onClick = { showBehaviours = true }) { Text(stringResource(R.string.object_add_behaviour)) }
                    DropdownMenu(expanded = showBehaviours, onDismissRequest = { showBehaviours = false }) {
                        for (kind in BehaviourKind.entries) {
                            DropdownMenuItem(text = { Text(stringResource(kind.label)) }, onClick = {
                                showBehaviours = false
                                save(current.copy(behaviours = current.behaviours + Behaviour(kind, path = if (kind == BehaviourKind.FOLLOW_PATH) path else null)))
                            })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            TextButton(onClick = {
                state.removeObject(current.id)
                onDismiss()
            }) { Text(stringResource(R.string.object_not_object)) }
        },
    )
}

/**
 * A number typed in place; the value changes once the text reads as a number. The text is the
 * field's own while typing (so "1." isn't rewritten under the finger): callers key the field
 * to what it edits.
 */
@Composable
internal fun NumberField(value: Double, onChange: (Double) -> Unit) {
    var text by remember { mutableStateOf(plainNumber(value)) }
    OutlinedTextField(
        text,
        { typed ->
            text = typed
            typed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let(onChange)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(96.dp),
    )
}

/** [value] as typed by hand: whole numbers without decimals, others as they are. */
internal fun plainNumber(value: Double): String =
    if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
