package com.andene.fern.canvas

import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.andene.fern.R

private const val EXAMPLE_GAME_SCRIPT = "// Collect coins; win at 10\nif touching(\"Coin\") [\n  removetouching \"Coin\"\n  addprop \"score\" 1\n]\nif prop(\"score\") >= 10 [ setstate \"win\" ]"

/**
 * An object's game script (roadmap item 164), written in [ScriptEngine]'s language with the
 * [GameScript] commands; it runs every step while the game plays. Saving checks it first and
 * names the line of the first error; an empty script removes it.
 */
@Composable
fun GameScriptDialog(state: CanvasState, objectId: String, onDismiss: () -> Unit) {
    val target = state.gameObjects.firstOrNull { it.id == objectId } ?: return onDismiss()
    var source by remember { mutableStateOf(target.script.ifEmpty { EXAMPLE_GAME_SCRIPT }) }
    var error by remember { mutableStateOf<ScriptError?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_script_title)) },
        text = {
            Column(Modifier.heightIn(max = FernScale.s512).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.game_script_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = source,
                    onValueChange = {
                        source = it
                        error = null
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = FernScale.s128).padding(top = FernScale.s8),
                )
                error?.let { Text(stringResource(R.string.script_error, it.line, it.message.orEmpty()), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = FernScale.s8)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = if (source.isBlank()) null else GameScript.check(source)
                if (error == null) {
                    state.updateObject(target.copy(script = source.trim()))
                    onDismiss()
                }
            }) { Text(stringResource(R.string.game_script_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
