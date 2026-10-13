package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import com.andene.fern.FernScale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import com.andene.fern.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plugins (roadmap item 111): the built-in and installed plugins; pick one to see what it does,
 * set its values and run it on the selection (one undo step). Install one from a .js file, write
 * a new one, or remove an installed one. Plugins run sandboxed (see [runPlugin]).
 */
@Composable
fun PluginsDialog(state: CanvasState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    val plugins = remember(revision) { PluginLibrary.list(context) }
    var chosen by remember { mutableStateOf<Plugin?>(null) }
    var writing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val values = remember(chosen) { mutableStateMapOf<String, String>().apply { chosen?.params?.forEach { put(it.name, formatMeasure(it.default)) } } }
    val install = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val installed = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("unreadable")
                    PluginLibrary.install(context, text, uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Plugin")
                }.getOrNull()
            }
            if (installed == null) error = context.getString(R.string.plugins_install_failed) else revision++
        }
    }

    writing?.let { source ->
        FernPanel(
            onDismissRequest = { writing = null },
            title = { Text(stringResource(R.string.plugins_new)) },
            text = {
                FernTextField(
                    source, { writing = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = FernScale.s256),
                )
            },
            confirmButton = {
                FernTextButton(onClick = {
                    PluginLibrary.install(context, source, "Plugin")
                    writing = null
                    revision++
                }) { Text(stringResource(R.string.plugins_save)) }
            },
            dismissButton = { FernTextButton(onClick = { writing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }

    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(chosen?.name ?: stringResource(R.string.plugins_title)) },
        text = {
            Column(Modifier.heightIn(max = FernScale.s512).verticalScroll(rememberScrollState())) {
                val plugin = chosen
                if (plugin == null) {
                    for (p in plugins) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { chosen = p; error = null }.padding(vertical = FernScale.s8)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                if (p.description.isNotEmpty()) Text(p.description, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!p.builtIn) IconButton(onClick = { PluginLibrary.remove(context, p.id); revision++ }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.plugins_remove))
                            }
                        }
                    }
                    Row {
                        FernTextButton(onClick = { install.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.plugins_install)) }
                        FernTextButton(onClick = { writing = PLUGIN_TEMPLATE }) { Text(stringResource(R.string.plugins_new)) }
                    }
                } else {
                    if (plugin.description.isNotEmpty()) Text(plugin.description)
                    for (param in plugin.params) {
                        FernTextField(
                            values[param.name].orEmpty(), { values[param.name] = it },
                            label = { Text(param.label) },
                            singleLine = true,
                            isError = values[param.name]?.replace(',', '.')?.toDoubleOrNull() == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val plugin = chosen
            if (plugin == null) {
                FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            } else {
                FernTextButton(enabled = !running, onClick = {
                    val params = values.mapValues { (_, v) -> v.replace(',', '.').toDoubleOrNull() }.filterValues { it != null }.mapValues { it.value!! }
                    val input = state.pluginInput()
                    running = true
                    scope.launch {
                        val result = withContext(Dispatchers.Default) { runPlugin(plugin, input, params) }
                        running = false
                        when (result) {
                            is PluginResult.Failed -> error = result.message
                            is PluginResult.Changes -> {
                                state.applyPluginChanges(result)
                                onDismiss()
                            }
                        }
                    }
                }) { Text(stringResource(R.string.plugins_run)) }
            }
        },
        dismissButton = {
            if (chosen != null) FernTextButton(onClick = { chosen = null; error = null }) { Text(stringResource(R.string.plugins_back)) }
        },
    )
}

/** A new plugin's starting point: the header and a run function that draws a line across the view. */
private val PLUGIN_TEMPLATE = """
    // @name My plugin
    // @description What it does.
    // @param size number 40 Size
    function run(doc, params) {
      var v = doc.view;
      var y = v.y + v.height / 2;
      return { add: [{ points: [[v.x + v.width / 2 - params.size, y], [v.x + v.width / 2 + params.size, y]] }] };
    }
""".trimIndent()
