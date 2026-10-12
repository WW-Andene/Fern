package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Text entry for the TEXT tool: creates a new label, or edits an existing one when
 * [initialText] is non-empty. Confirming with a blank/whitespace-only string deletes the
 * label being edited (or simply places nothing new) - one gesture for both "clear the text"
 * and "remove the label", matching how most text tools treat an emptied text box.
 */
@Composable
fun TextEditDialog(
    initialText: String,
    initialBold: Boolean,
    initialItalic: Boolean,
    onConfirm: (text: String, bold: Boolean, italic: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var bold by remember { mutableStateOf(initialBold) }
    var italic by remember { mutableStateOf(initialItalic) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialText.isEmpty()) "Add text" else "Edit text") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    textStyle = LocalTextStyle.current.copy(
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    ),
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(
                        selected = bold,
                        onClick = { bold = !bold },
                        label = { Text("Bold", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = italic,
                        onClick = { italic = !italic },
                        label = { Text("Italic", fontStyle = FontStyle.Italic) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, bold, italic) }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
