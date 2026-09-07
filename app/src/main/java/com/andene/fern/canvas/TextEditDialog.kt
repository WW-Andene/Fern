package com.andene.fern.canvas

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Text entry for the TEXT tool: creates a new label, or edits an existing one when
 * [initialText] is non-empty. Confirming with a blank/whitespace-only string deletes the
 * label being edited (or simply places nothing new) - one gesture for both "clear the text"
 * and "remove the label", matching how most text tools treat an emptied text box.
 */
@Composable
fun TextEditDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialText.isEmpty()) "Add text" else "Edit text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
