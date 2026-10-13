package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernSwitch
import com.andene.fern.FernTextField
import com.andene.fern.FernPanel

import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import com.andene.fern.FernMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * An object's state machine (roadmap item 158): its states - each playing a frame tag, looping or
 * not, one of them the first - and the transitions between them with their conditions.
 * Changes save as they're made.
 */
@Composable
fun StateMachineDialog(state: CanvasState, objectId: String, onDismiss: () -> Unit) {
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.states_title)) },
        text = { StateMachineEditor(state, objectId) },
    )
}

/** The editor itself, shown in its own panel or as a tab of the object panel (design roadmap D46). */
@Composable
fun StateMachineEditor(state: CanvasState, objectId: String) {
    val target = state.gameObjects.firstOrNull { it.id == objectId } ?: return
    val machine = target.stateMachine ?: StateMachine(emptyList(), "", emptyList())
    fun save(m: StateMachine) = state.updateObject(target.copy(stateMachine = m.takeIf { it.states.isNotEmpty() }))
    var newState by remember { mutableStateOf("") }
    Column(Modifier.heightIn(max = FernScale.s512).verticalScroll(rememberScrollState())) {
        if (machine.states.isEmpty()) Text(stringResource(R.string.states_empty), style = MaterialTheme.typography.bodySmall)
        for ((i, s) in machine.states.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = machine.initial == s.name, onClick = { save(machine.copy(initial = s.name)) })
                Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    save(machine.copy(states = machine.states.filterIndexed { k, _ -> k != i }, transitions = machine.transitions.filter { it.to != s.name && it.from != s.name }))
                }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.states_delete)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Choice(s.tag ?: stringResource(R.string.states_no_tag), listOf(null) + state.frameTags.map { it.name }, { it ?: "—" }) { tag ->
                    save(machine.copy(states = machine.states.mapIndexed { k, x -> if (k == i) x.copy(tag = tag) else x }))
                }
                Text(stringResource(R.string.states_loop), modifier = Modifier.padding(start = FernScale.s8))
                FernSwitch(checked = s.loop, onCheckedChange = { loop -> save(machine.copy(states = machine.states.mapIndexed { k, x -> if (k == i) x.copy(loop = loop) else x })) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FernTextField(newState, { newState = it }, label = { Text(stringResource(R.string.states_new)) }, singleLine = true, modifier = Modifier.weight(1f))
            FernTextButton(onClick = {
                save(machine.copy(states = machine.states + AnimState(newState.trim()), initial = machine.initial.ifEmpty { newState.trim() }))
                newState = ""
            }, enabled = newState.isNotBlank() && machine.states.none { it.name == newState.trim() }) { Text(stringResource(R.string.object_add)) }
        }
        HorizontalDivider(Modifier.padding(vertical = FernScale.s8))
        Text(stringResource(R.string.states_transitions), style = MaterialTheme.typography.titleSmall)
        val names = machine.states.map { it.name }
        for ((i, t) in machine.transitions.withIndex()) {
            fun update(change: Transition) = save(machine.copy(transitions = machine.transitions.mapIndexed { k, x -> if (k == i) change else x }))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Choice(t.from ?: stringResource(R.string.states_any), listOf<String?>(null) + names, { it ?: "*" }) { update(t.copy(from = it)) }
                Text(" → ")
                Choice(t.to, names, { it }) { update(t.copy(to = it)) }
                IconButton(onClick = { save(machine.copy(transitions = machine.transitions.filterIndexed { k, _ -> k != i })) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.states_delete_transition))
                }
            }
            ConditionsEditor(t.conditions) { update(t.copy(conditions = it)) }
            HorizontalDivider()
        }
        if (names.isNotEmpty()) FernTextButton(onClick = { save(machine.copy(transitions = machine.transitions + Transition(null, names.first(), emptyList()))) }) {
            Text(stringResource(R.string.states_add_transition))
        }
    }

}

/** A list of conditions to edit: each one's kind, and its name and number where it uses them. */
@Composable
fun ConditionsEditor(conditions: List<Condition>, onChange: (List<Condition>) -> Unit) {
    for ((i, c) in conditions.withIndex()) {
        fun update(x: Condition) = onChange(conditions.mapIndexed { k, y -> if (k == i) x else y })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Choice(stringResource(c.kind.label), ConditionKind.entries, { it.name }) { update(c.copy(kind = it)) }
            if (c.kind.needsText) FernTextField(c.text, { update(c.copy(text = it)) }, singleLine = true, modifier = Modifier.weight(1f))
            if (c.kind.needsValue) NumberField(c.value) { update(c.copy(value = it)) }
            IconButton(onClick = { onChange(conditions.filterIndexed { k, _ -> k != i }) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.condition_remove))
            }
        }
    }
    FernTextButton(onClick = { onChange(conditions + Condition(ConditionKind.GROUNDED)) }) { Text(stringResource(R.string.condition_add)) }
}

/** A button showing [current] that opens a menu of [options]. */
@Composable
internal fun <T> Choice(current: String, options: List<T>, label: @Composable (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FernTextButton(onClick = { open = true }) { Text(current) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (o in options) {
                val text = if (o is ConditionKind) stringResource(o.label) else label(o)
                FernMenuItem(text = { Text(text) }, onClick = { open = false; onPick(o) })
            }
        }
    }
}
