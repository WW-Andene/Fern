package com.andene.fern.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * The symbol library (roadmap item 100): tap a symbol to place an instance at the centre of the
 * view, linked to it; the bin deletes it from the library (placed instances stay, unlinked when
 * next opened). With strokes selected, they can be saved as a new symbol under a name; with a
 * placed instance selected, its symbol can be redefined from the selection - every instance
 * follows - or the instance detached into plain strokes.
 */
@Composable
fun SymbolsDialog(
    symbols: List<SymbolMeta>,
    canSaveSelection: Boolean,
    /** The symbol the selection is a linked instance of, if it is one. */
    selectedInstanceOf: SymbolMeta?,
    onRedefine: () -> Unit,
    onDetach: () -> Unit,
    onInsert: (SymbolMeta) -> Unit,
    onDelete: (SymbolMeta) -> Unit,
    onSaveSelection: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.symbols_title)) },
        text = {
            Column {
                if (symbols.isEmpty()) Text(stringResource(R.string.symbols_empty))
                LazyColumn(modifier = Modifier.heightIn(max = 256.dp)) {
                    items(symbols, key = { it.id }) { symbol ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(symbol.name, modifier = Modifier.weight(1f).clickable { onInsert(symbol) }.padding(vertical = 12.dp))
                            IconButton(onClick = { onDelete(symbol) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.symbols_delete, symbol.name))
                            }
                        }
                    }
                }
                selectedInstanceOf?.let { symbol ->
                    // A selected instance (roadmap item 100): redefine its symbol everywhere, or unlink it.
                    TextButton(onClick = onRedefine) { Text(stringResource(R.string.symbols_redefine, symbol.name)) }
                    TextButton(onClick = onDetach) { Text(stringResource(R.string.symbols_detach)) }
                }
                if (canSaveSelection) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.symbols_name)) }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            onSaveSelection(name)
                            name = ""
                        }) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
