package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernSwitch
import com.andene.fern.FernTextField
import com.andene.fern.FernPanel

import com.andene.fern.FernScale

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
import androidx.compose.material3.DropdownMenu
import com.andene.fern.FernMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.andene.fern.MoreOptions
import com.andene.fern.R
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.foundation.horizontalScroll

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
    // One panel for the object and its logic, by tab (design roadmap D46).
    var tab by remember { mutableStateOf(0) }
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.object_title)) },
        text = {
            Column {
            // Every object, to switch to another without closing the panel.
            if (state.gameObjects.size > 1) Row(Modifier.horizontalScroll(rememberScrollState())) {
                for (o in state.gameObjects) {
                    FilterChip(selected = o.id == current.id, onClick = { current = o }, label = { Text(o.name) }, modifier = Modifier.padding(end = FernScale.s8))
                }
            }
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = FernScale.none) {
                for ((index, label) in listOf(R.string.object_tab_object, R.string.states_title, R.string.rules_title, R.string.game_script_title).withIndex()) {
                    Tab(selected = tab == index, onClick = {
                        tab = index
                        state.gameObjects.firstOrNull { it.id == current.id }?.let { current = it }
                    }, text = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                1 -> StateMachineEditor(state, current.id)
                2 -> RulesEditor(state, current.id)
                3 -> GameScriptEditor(state, current.id)
                else -> Column(Modifier.heightIn(max = FernScale.s512).verticalScroll(rememberScrollState())) {
                FernTextField(current.name, { save(current.copy(name = it)) }, label = { Text(stringResource(R.string.object_name)) }, singleLine = true)
                Text(stringResource(R.string.object_properties), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FernScale.s8))
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
                    FernTextField(newProperty, { newProperty = it.filter { c -> c.isLetterOrDigit() || c == '_' } }, label = { Text(stringResource(R.string.object_new_property)) }, singleLine = true, modifier = Modifier.weight(1f))
                    FernTextButton(onClick = {
                        save(current.copy(properties = current.properties + (newProperty to 0.0)))
                        newProperty = ""
                    }, enabled = newProperty.isNotEmpty() && newProperty !in current.properties) { Text(stringResource(R.string.object_add)) }
                }
                HorizontalDivider(Modifier.padding(vertical = FernScale.s8))
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
                        if (path != null) FernTextButton(onClick = {
                            save(current.copy(behaviours = current.behaviours.mapIndexed { i, b -> if (i == index) b.copy(path = path) else b }))
                        }) { Text(stringResource(R.string.object_use_path)) }
                    }
                }
                // On screen while playing (roadmap item 161).
                HorizontalDivider(Modifier.padding(vertical = FernScale.s8))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.object_show_in_hud), modifier = Modifier.weight(1f))
                    FernSwitch(checked = current.showInHud, onCheckedChange = { save(current.copy(showInHud = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.object_camera_follows), modifier = Modifier.weight(1f))
                    FernSwitch(
                        checked = state.gameSettings.cameraTarget == current.id,
                        onCheckedChange = { follow -> state.changeGameSettings(state.gameSettings.copy(cameraTarget = if (follow) current.id else null)) },
                    )
                }
                // Physics, hitbox and links: one tap away, not in the way (design roadmap D32).
                MoreOptions("object_advanced") {
                    PhysicsSection(state, current.body) { save(current.copy(body = it)) }
                    // Drawn hitbox and links (roadmap item 157).
                    HorizontalDivider(Modifier.padding(vertical = FernScale.s8))
                    Text(stringResource(if (current.hitbox == null) R.string.object_no_hitbox else R.string.object_has_hitbox), style = MaterialTheme.typography.bodySmall)
                    if (current.hitbox != null) FernTextButton(onClick = { save(current.copy(hitbox = null)) }) { Text(stringResource(R.string.object_clear_hitbox)) }
                    for (link in state.gameSettings.links.filter { it.from == current.id || it.to == current.id }) {
                        val other = state.gameObjects.firstOrNull { it.id == (if (link.from == current.id) link.to else link.from) }?.name ?: "?"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.object_link_row, stringResource(linkLabel(link.kind)), other), modifier = Modifier.weight(1f))
                            IconButton(onClick = { state.removeLink(link.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.object_remove_link)) }
                        }
                    }
                }
                Box {
                    FernTextButton(onClick = { showBehaviours = true }) { Text(stringResource(R.string.object_add_behaviour)) }
                    DropdownMenu(expanded = showBehaviours, onDismissRequest = { showBehaviours = false }) {
                        for (kind in BehaviourKind.entries) {
                            FernMenuItem(text = { Text(stringResource(kind.label)) }, onClick = {
                                showBehaviours = false
                                save(current.copy(behaviours = current.behaviours + Behaviour(kind, path = if (kind == BehaviourKind.FOLLOW_PATH) path else null)))
                            })
                        }
                    }
                }
            }
            }
            }
        },
        dismissButton = {
            FernTextButton(onClick = {
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
    FernTextField(
        text,
        { typed ->
            text = typed
            typed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let(onChange)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(FernScale.s96),
    )
}

/** [value] as typed by hand: whole numbers without decimals, others as they are. */
internal fun plainNumber(value: Double): String =
    if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

/** An object's physics (roadmap item 159), and the world's gravity and walls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhysicsSection(state: CanvasState, body: PhysicsBody, onChange: (PhysicsBody) -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = FernScale.s8))
    Text(stringResource(R.string.physics_title), style = MaterialTheme.typography.titleSmall)
    FlowRow {
        for ((type, label) in listOf(BodyType.NONE to R.string.physics_none, BodyType.STATIC to R.string.physics_static, BodyType.DYNAMIC to R.string.physics_dynamic)) {
            FilterChip(selected = body.type == type, onClick = { onChange(body.copy(type = type)) }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = FernScale.s8))
        }
    }
    if (body.type == BodyType.NONE) return
    FlowRow {
        for ((shape, label) in listOf(BodyShape.BOX to R.string.physics_box, BodyShape.CIRCLE to R.string.physics_circle)) {
            FilterChip(selected = body.shape == shape, onClick = { onChange(body.copy(shape = shape)) }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = FernScale.s8))
        }
    }
    LabelledNumber(R.string.physics_bounce, body.bounce) { onChange(body.copy(bounce = it.coerceIn(0.0, 1.0))) }
    LabelledNumber(R.string.physics_friction, body.friction) { onChange(body.copy(friction = it.coerceIn(0.0, 1.0))) }
    if (body.type == BodyType.DYNAMIC) {
        LabelledNumber(R.string.physics_mass, body.mass) { onChange(body.copy(mass = it.coerceAtLeast(0.001))) }
        LabelledNumber(R.string.physics_gravity_scale, body.gravityScale) { onChange(body.copy(gravityScale = it)) }
    }
    val world = state.gameSettings
    Text(stringResource(R.string.physics_world), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FernScale.s8))
    LabelledNumber(R.string.physics_gravity_down, world.gravityY) { state.changeGameSettings(world.copy(gravityY = it)) }
    LabelledNumber(R.string.physics_gravity_across, world.gravityX) { state.changeGameSettings(world.copy(gravityX = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.physics_walls), modifier = Modifier.weight(1f))
        FernSwitch(checked = world.walls, onCheckedChange = { state.changeGameSettings(world.copy(walls = it)) })
    }
    SceneChooser(state)
}

/** Which frame the game plays in (roadmap item 161), or the view as it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneChooser(state: CanvasState) {
    val settings = state.gameSettings
    Text(stringResource(R.string.play_scene), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FernScale.s8))
    FlowRow {
        FilterChip(selected = settings.sceneFrameId == null, onClick = { state.changeGameSettings(settings.copy(sceneFrameId = null)) }, label = { Text(stringResource(R.string.play_scene_view)) }, modifier = Modifier.padding(end = FernScale.s8))
        for (frame in state.frames) {
            FilterChip(selected = settings.sceneFrameId == frame.id, onClick = { state.changeGameSettings(settings.copy(sceneFrameId = frame.id)) }, label = { Text(frame.name) }, modifier = Modifier.padding(end = FernScale.s8))
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
