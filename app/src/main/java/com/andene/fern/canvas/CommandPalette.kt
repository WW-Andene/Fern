package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernTextField
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import com.andene.fern.FernListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.andene.fern.FernPanel
import com.andene.fern.FernSize
import com.andene.fern.R
import java.text.Normalizer
import java.util.Locale

/**
 * One command in the palette (design roadmap D14): its [label] in the app's language and the
 * other one ([otherLabel], so a French speaker can type an English name and the reverse), where
 * it otherwise lives ([location]), its keyboard [shortcut] if any, and what it does.
 */
class PaletteCommand(val label: String, val otherLabel: String, val location: String, val shortcut: String? = null, val run: () -> Unit)

/** Whether the command palette is open; also opened by Ctrl/⌘ K. */
object CommandPaletteState {
    var open by mutableStateOf(false)
}

/** Builds commands with labels in both of the app's languages. */
class CommandLabels(private val context: Context) {
    private val other: Resources = run {
        val current = context.resources.configuration.locales[0].language
        val config = Configuration(context.resources.configuration).apply { setLocale(if (current == "fr") Locale.ENGLISH else Locale.FRENCH) }
        context.createConfigurationContext(config).resources
    }

    fun command(@StringRes label: Int, @StringRes location: Int, shortcut: String? = null, run: () -> Unit) =
        PaletteCommand(context.getString(label), other.getString(label), context.getString(location), shortcut, run)
}

@Composable
fun rememberCommandLabels(): CommandLabels {
    val context = LocalContext.current
    return remember(context) { CommandLabels(context) }
}

/** [text] lower-cased without accents, so "calque" finds "Calque" and "selection" finds "Sélection". */
fun searchKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

/** The commands matching every word of [query], in either language or by location; best (label starts with it) first. */
fun matchCommands(commands: List<PaletteCommand>, query: String): List<PaletteCommand> {
    val words = searchKey(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return commands
    return commands.filter { c ->
        val hay = searchKey("${c.label} ${c.otherLabel} ${c.location}")
        words.all { it in hay }
    }.sortedByDescending { searchKey(it.label).startsWith(words.first()) || searchKey(it.otherLabel).startsWith(words.first()) }
}

/**
 * The command palette (design roadmap D14): type to find any tool, action, setting, export or
 * document by name in English or French; each result shows where it otherwise lives and its
 * shortcut. Enter runs the first result.
 */
@Composable
fun CommandPalette(commands: List<PaletteCommand>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = matchCommands(commands, query)
    fun run(command: PaletteCommand) {
        onDismiss()
        command.run()
    }
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.palette_title)) },
        text = {
            Column {
                FernTextField(
                    query,
                    { query = it },
                    leadingIcon = { Icon(FernIcons.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.palette_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(::run) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                if (results.isEmpty()) Text(stringResource(R.string.palette_nothing))
                LazyColumn(Modifier.heightIn(max = FernSize.dialogMaxHeight)) {
                    items(results) { c ->
                        FernListItem(
                            headlineContent = { Text(c.label) },
                            supportingContent = { Text(c.location) },
                            trailingContent = c.shortcut?.let { { Text(it) } },
                            modifier = Modifier.clickable { run(c) },
                        )
                    }
                }
            }
        },
    )
}
