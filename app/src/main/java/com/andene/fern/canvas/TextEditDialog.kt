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
import androidx.compose.ui.platform.LocalUriHandler
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
    initialSticky: Boolean,
    initialSizePx: Double,
    onConfirm: (text: String, bold: Boolean, italic: Boolean, sticky: Boolean, sizePx: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var bold by remember { mutableStateOf(initialBold) }
    var italic by remember { mutableStateOf(initialItalic) }
    var sticky by remember { mutableStateOf(initialSticky) }
    // Snap to the nearest offered size, so an existing label shows its closest size selected.
    var sizePx by remember { mutableStateOf(TEXT_SIZES_PX.minBy { kotlin.math.abs(it - initialSizePx) }) }

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
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = sticky,
                        onClick = { sticky = !sticky },
                        label = { Text("Sticky note") },
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    for ((size, label) in TEXT_SIZES_PX.zip(listOf("S", "M", "L", "XL"))) {
                        FilterChip(
                            selected = sizePx == size,
                            onClick = { sizePx = size },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, bold, italic, sticky, sizePx) }) { Text("Done") }
        },
        dismissButton = {
            Row {
                // A label holding a web address works as a link on the board: open it from here.
                val link = remember(text) { firstWebLink(text) }
                val uriHandler = LocalUriHandler.current
                if (link != null) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) { Text("Open link") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// Text sizes offered, in screen px at the zoom the label is placed at; all on the §7 scale.
private val TEXT_SIZES_PX = listOf(16.0, 24.0, 32.0, 48.0)

/** The first web address in [text], with https:// added when it was typed without a scheme; null if none. */
private fun firstWebLink(text: String): String? {
    val matcher = android.util.Patterns.WEB_URL.matcher(text)
    if (!matcher.find()) return null
    val url = matcher.group()
    return if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else "https://$url"
}
