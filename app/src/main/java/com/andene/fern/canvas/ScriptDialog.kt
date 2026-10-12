package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.andene.fern.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXAMPLE_SCRIPT = "// A star\ncolor #2E7D32 width 4\nrepeat 5 [ forward 200 turn 144 ]"

/**
 * Write and run a drawing script ([DrawScript], roadmap items 111 and 164). Run draws it in the
 * middle of the view and closes; an error stays open, naming the line. Scripts run off the main
 * thread, sandboxed.
 */
@Composable
fun ScriptDialog(onRun: (List<ScriptLine>) -> Unit, onDismiss: () -> Unit) {
    var source by remember { mutableStateOf(EXAMPLE_SCRIPT) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val errorTemplate = stringResource(R.string.script_error)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.script_title)) },
        text = {
            Column {
                Text(stringResource(R.string.script_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = source,
                    onValueChange = {
                        source = it
                        error = null
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp).padding(top = 8.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val script = source
                scope.launch {
                    when (val result = withContext(Dispatchers.Default) { DrawScript.run(script) }) {
                        is ScriptResult.Drawn -> onRun(result.lines)
                        is ScriptResult.Failed -> error = errorTemplate.format(result.line, result.message)
                    }
                }
            }) { Text(stringResource(R.string.script_run)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
