package com.andene.fern.canvas

import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.annotation.StringRes
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
    var showStates by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showScript by remember { mutableStateOf(false) }
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
                // On screen while playing (roadmap item 161).
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.object_show_in_hud), modifier = Modifier.weight(1f))
                    Switch(checked = current.showInHud, onCheckedChange = { save(current.copy(showInHud = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.object_camera_follows), modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.gameSettings.cameraTarget == current.id,
                        onCheckedChange = { follow -> state.changeGameSettings(state.gameSettings.copy(cameraTarget = if (follow) current.id else null)) },
                    )
                }
                // Animation states (roadmap item 158).
                TextButton(onClick = { showStates = true }) { Text(stringResource(R.string.states_title)) }
                if (showStates) StateMachineDialog(state, current.id, onDismiss = {
                    showStates = false
                    state.gameObjects.firstOrNull { it.id == current.id }?.let { current = it }
                })
                // Rules from blocks (roadmap item 156) and the object's script (item 164).
                TextButton(onClick = { showRules = true }) { Text(stringResource(R.string.rules_title_count, current.rules.size)) }
                if (showRules) RulesDialog(state, current.id, onDismiss = {
                    showRules = false
                    state.gameObjects.firstOrNull { it.id == current.id }?.let { current = it }
                })
                TextButton(onClick = { showScript = true }) {
                    Text(stringResource(if (current.script.isEmpty()) R.string.game_script_add else R.string.game_script_edit))
                }
                if (showScript) GameScriptDialog(state, current.id, onDismiss = {
                    showScript = false
                    state.gameObjects.firstOrNull { it.id == current.id }?.let { current = it }
                })
                PhysicsSection(state, current.body) { save(current.copy(body = it)) }
                // Drawn hitbox and links (roadmap item 157).
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(if (current.hitbox == null) R.string.object_no_hitbox else R.string.object_has_hitbox), style = MaterialTheme.typography.bodySmall)
                if (current.hitbox != null) TextButton(onClick = { save(current.copy(hitbox = null)) }) { Text(stringResource(R.string.object_clear_hitbox)) }
                for (link in state.gameSettings.links.filter { it.from == current.id || it.to == current.id }) {
                    val other = state.gameObjects.firstOrNull { it.id == (if (link.from == current.id) link.to else link.from) }?.name ?: "?"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.object_link_row, stringResource(linkLabel(link.kind)), other), modifier = Modifier.weight(1f))
                        IconButton(onClick = { state.removeLink(link.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.object_remove_link)) }
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

/** An object's physics (roadmap item 159), and the world's gravity and walls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhysicsSection(state: CanvasState, body: PhysicsBody, onChange: (PhysicsBody) -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(stringResource(R.string.physics_title), style = MaterialTheme.typography.titleSmall)
    FlowRow {
        for ((type, label) in listOf(BodyType.NONE to R.string.physics_none, BodyType.STATIC to R.string.physics_static, BodyType.DYNAMIC to R.string.physics_dynamic)) {
            FilterChip(selected = body.type == type, onClick = { onChange(body.copy(type = type)) }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = 8.dp))
        }
    }
    if (body.type == BodyType.NONE) return
    FlowRow {
        for ((shape, label) in listOf(BodyShape.BOX to R.string.physics_box, BodyShape.CIRCLE to R.string.physics_circle)) {
            FilterChip(selected = body.shape == shape, onClick = { onChange(body.copy(shape = shape)) }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = 8.dp))
        }
    }
    LabelledNumber(R.string.physics_bounce, body.bounce) { onChange(body.copy(bounce = it.coerceIn(0.0, 1.0))) }
    LabelledNumber(R.string.physics_friction, body.friction) { onChange(body.copy(friction = it.coerceIn(0.0, 1.0))) }
    if (body.type == BodyType.DYNAMIC) {
        LabelledNumber(R.string.physics_mass, body.mass) { onChange(body.copy(mass = it.coerceAtLeast(0.001))) }
        LabelledNumber(R.string.physics_gravity_scale, body.gravityScale) { onChange(body.copy(gravityScale = it)) }
    }
    val world = state.gameSettings
    Text(stringResource(R.string.physics_world), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    LabelledNumber(R.string.physics_gravity_down, world.gravityY) { state.changeGameSettings(world.copy(gravityY = it)) }
    LabelledNumber(R.string.physics_gravity_across, world.gravityX) { state.changeGameSettings(world.copy(gravityX = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.physics_walls), modifier = Modifier.weight(1f))
        Switch(checked = world.walls, onCheckedChange = { state.changeGameSettings(world.copy(walls = it)) })
    }
    SceneChooser(state)
}

/** Which frame the game plays in (roadmap item 161), or the view as it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneChooser(state: CanvasState) {
    val settings = state.gameSettings
    Text(stringResource(R.string.play_scene), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow {
        FilterChip(selected = settings.sceneFrameId == null, onClick = { state.changeGameSettings(settings.copy(sceneFrameId = null)) }, label = { Text(stringResource(R.string.play_scene_view)) }, modifier = Modifier.padding(end = 8.dp))
        for (frame in state.frames) {
            FilterChip(selected = settings.sceneFrameId == frame.id, onClick = { state.changeGameSettings(settings.copy(sceneFrameId = frame.id)) }, label = { Text(frame.name) }, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun LabelledNumber(@StringRes label: Int, value: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        NumberField(value, onChange)
    }
}

private fun linkLabel(kind: LinkKind) = when (kind) {
    LinkKind.ROD -> R.string.link_rod
    LinkKind.ROPE -> R.string.link_rope
    LinkKind.ATTACH -> R.string.link_attach
}
