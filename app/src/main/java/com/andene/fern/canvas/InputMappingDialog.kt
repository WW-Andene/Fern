package com.andene.fern.canvas

import com.andene.fern.FernScale

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * The game's controls (roadmap item 160): each action and the inputs bound to it. Add a key or
 * gamepad button by pressing it, a stick direction, an on-screen button, a finger anywhere or
 * the stylus; remove any; add, rename or delete actions; or go back to the defaults.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputMappingDialog(state: CanvasState, onDismiss: () -> Unit) {
    val actions = state.gameSettings.actions
    fun save(updated: List<InputAction>) = state.changeGameSettings(state.gameSettings.copy(actions = updated))
    fun bindingsOf(index: Int, change: (List<InputBinding>) -> List<InputBinding>) =
        save(actions.mapIndexed { i, a -> if (i == index) a.copy(bindings = change(a.bindings).distinct()) else a })
    var capturing by remember { mutableStateOf<Int?>(null) }
    var newAction by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(capturing) { if (capturing != null) focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.controls_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = FernScale.s512)
                    .verticalScroll(rememberScrollState())
                    .focusRequester(focus)
                    .focusable()
                    // While waiting for a key, the next key or gamepad button pressed is bound.
                    .onPreviewKeyEvent { event ->
                        val index = capturing ?: return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        bindingsOf(index) { it + InputBinding.Key(event.key.nativeKeyCode) }
                        capturing = null
                        true
                    },
            ) {
                for ((index, action) in actions.withIndex()) key(action.name) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(action.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { save(actions.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.controls_delete_action))
                        }
                    }
                    FlowRow {
                        for (binding in action.bindings) {
                            InputChip(
                                selected = false,
                                onClick = { bindingsOf(index) { it - binding } },
                                label = { Text(bindingLabel(binding)) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.controls_remove_binding)) },
                                modifier = Modifier.padding(end = FernScale.s4),
                            )
                        }
                    }
                    var adding by remember { mutableStateOf(false) }
                    Row {
                        TextButton(onClick = { capturing = index }) {
                            Text(stringResource(if (capturing == index) R.string.controls_press_key else R.string.controls_add_key))
                        }
                        Box {
                            TextButton(onClick = { adding = true }) { Text(stringResource(R.string.controls_add_other)) }
                            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                                val others = TouchButton.entries.map { InputBinding.Touch(it) } +
                                    listOf(InputBinding.Axis(AXIS_X, false), InputBinding.Axis(AXIS_X, true), InputBinding.Axis(AXIS_Y, false), InputBinding.Axis(AXIS_Y, true), InputBinding.Screen, InputBinding.Stylus)
                                for (other in others) {
                                    DropdownMenuItem(text = { Text(bindingLabel(other)) }, onClick = {
                                        adding = false
                                        bindingsOf(index) { it + other }
                                    })
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newAction, { newAction = it.filter { c -> c.isLetterOrDigit() || c == '_' } }, label = { Text(stringResource(R.string.controls_new_action)) }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        save(actions + InputAction(newAction, emptyList()))
                        newAction = ""
                    }, enabled = newAction.isNotEmpty() && actions.none { it.name == newAction }) { Text(stringResource(R.string.object_add)) }
                }
                TextButton(onClick = { save(DEFAULT_ACTIONS) }) { Text(stringResource(R.string.controls_defaults)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** How a binding reads: the key's name, the stick and direction, the button, finger or stylus. */
@Composable
private fun bindingLabel(binding: InputBinding): String = when (binding) {
    is InputBinding.Key -> KeyEvent.keyCodeToString(binding.code).removePrefix("KEYCODE_").replace('_', ' ').lowercase()
    is InputBinding.Axis -> stringResource(
        when {
            binding.axis == AXIS_X && !binding.positive -> R.string.controls_stick_left
            binding.axis == AXIS_X -> R.string.controls_stick_right
            !binding.positive -> R.string.controls_stick_up
            else -> R.string.controls_stick_down
        },
    )
    is InputBinding.Touch -> stringResource(R.string.controls_touch_button, binding.button.name.lowercase())
    InputBinding.Screen -> stringResource(R.string.controls_screen)
    InputBinding.Stylus -> stringResource(R.string.controls_stylus)
}
