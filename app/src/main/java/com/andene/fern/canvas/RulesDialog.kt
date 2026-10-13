package com.andene.fern.canvas

import com.andene.fern.FernPanel

import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * An object's rules as blocks (roadmap item 156): each rule a block holding its "when" blocks
 * (conditions) and its "do" blocks (actions), in the order they run - add, change, move up,
 * remove. Changes save as they're made.
 */
@Composable
fun RulesDialog(state: CanvasState, objectId: String, onDismiss: () -> Unit) {
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_title)) },
        text = { RulesEditor(state, objectId) },
    )
}

/** The editor itself, shown in its own panel or as a tab of the object panel (design roadmap D46). */
@Composable
fun RulesEditor(state: CanvasState, objectId: String) {
    val target = state.gameObjects.firstOrNull { it.id == objectId } ?: return
    val rules = target.rules
    fun save(updated: List<Rule>) = state.updateObject(target.copy(rules = updated))
    Column(Modifier.heightIn(max = FernScale.s512).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.rules_help), style = MaterialTheme.typography.bodySmall)
        if (rules.isEmpty()) Text(stringResource(R.string.rules_empty), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = FernScale.s8))
        for ((i, rule) in rules.withIndex()) {
            fun update(change: Rule) = save(rules.mapIndexed { k, r -> if (k == i) change else r })
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = FernScale.s8)) {
                Column(Modifier.padding(FernScale.s8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rules_when), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (i > 0) IconButton(onClick = { save(rules.toMutableList().apply { add(i - 1, removeAt(i)) }) }) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.rules_move_up))
                        }
                        IconButton(onClick = { save(rules.filterIndexed { k, _ -> k != i }) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.rules_delete))
                        }
                    }
                    if (rule.conditions.isEmpty()) Text(stringResource(R.string.rules_always), style = MaterialTheme.typography.bodySmall)
                    ConditionsEditor(rule.conditions) { update(rule.copy(conditions = it)) }
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(FernScale.s8)) {
                            Text(stringResource(R.string.rules_do), style = MaterialTheme.typography.titleSmall)
                            ActionsEditor(rule.actions) { update(rule.copy(actions = it)) }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { save(rules + Rule(listOf(Condition(ConditionKind.ACTION_PRESSED, "jump")), emptyList())) }) {
            Text(stringResource(R.string.rules_add))
        }
    }

}

/** A list of actions to edit: each one's kind, and its name and number where it uses them. */
@Composable
private fun ActionsEditor(actions: List<RuleAction>, onChange: (List<RuleAction>) -> Unit) {
    for ((i, a) in actions.withIndex()) {
        fun update(x: RuleAction) = onChange(actions.mapIndexed { k, y -> if (k == i) x else y })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Choice(stringResource(a.kind.label), ActionKind.entries, { stringResource(it.label) }) { update(a.copy(kind = it)) }
            if (a.kind.needsText) OutlinedTextField(a.text, { update(a.copy(text = it)) }, singleLine = true, modifier = Modifier.weight(1f))
            if (a.kind.needsValue) NumberField(a.value) { update(a.copy(value = it)) }
            IconButton(onClick = { onChange(actions.filterIndexed { k, _ -> k != i }) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.rules_remove_action))
            }
        }
    }
    TextButton(onClick = { onChange(actions + RuleAction(ActionKind.ADD_TO_PROPERTY, "score", 1.0)) }) { Text(stringResource(R.string.rules_add_action)) }
}
