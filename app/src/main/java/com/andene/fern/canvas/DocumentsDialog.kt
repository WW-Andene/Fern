package com.andene.fern.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Lists every saved document with open/rename/duplicate/delete actions, plus creating a new
 * one. The currently open document is shown in bold and can't be deleted while it's the only
 * document (a canvas with zero documents isn't a valid state).
 */
@Composable
fun DocumentsDialog(
    documents: List<DocumentMeta>,
    currentDocumentId: String,
    onSelect: (DocumentMeta) -> Unit,
    onRename: (DocumentMeta, String) -> Unit,
    onDuplicate: (DocumentMeta) -> Unit,
    onDelete: (DocumentMeta) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Documents") },
        text = {
            Column {
                for (doc in documents) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (renamingId == doc.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                onRename(doc, renameText)
                                renamingId = null
                            }) {
                                Icon(Icons.Filled.Check, contentDescription = "Confirm rename")
                            }
                        } else {
                            Text(
                                text = doc.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelect(doc) },
                                fontWeight = if (doc.id == currentDocumentId) FontWeight.Bold else FontWeight.Normal,
                            )
                            IconButton(onClick = {
                                renamingId = doc.id
                                renameText = doc.name
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { onDuplicate(doc) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                            }
                            IconButton(onClick = { onDelete(doc) }, enabled = documents.size > 1) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) { Text("New document") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
