package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** What the text dialog was confirmed with; see [CanvasState.confirmTextEdit]. */
data class TextEditResult(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val sticky: Boolean,
    val sizePx: Double,
    val font: TextFont,
    val alignment: TextAlignment,
    /** False when the label was switched from a text box to a single line. */
    val keepBox: Boolean,
)

/**
 * Text entry for the TEXT tool: creates a new label, or edits an existing one when the
 * pending edit's text is non-empty. Confirming with a blank/whitespace-only string deletes the
 * label being edited (or simply places nothing new) - one gesture for both "clear the text"
 * and "remove the label", matching how most text tools treat an emptied text box.
 */
@Composable
fun TextEditDialog(
    pending: CanvasState.PendingTextEdit,
    onConfirm: (TextEditResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(pending.initialText) }
    var bold by remember { mutableStateOf(pending.initialBold) }
    var italic by remember { mutableStateOf(pending.initialItalic) }
    var sticky by remember { mutableStateOf(pending.initialSticky) }
    // Snap to the nearest offered size, so an existing label shows its closest size selected.
    var sizePx by remember { mutableStateOf(TEXT_SIZES_PX.minBy { kotlin.math.abs(it - pending.initialSizePx) }) }
    var font by remember { mutableStateOf(pending.initialFont) }
    var alignment by remember { mutableStateOf(pending.initialAlignment) }
    var keepBox by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pending.initialText.isEmpty()) "Add text" else "Edit text") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    textStyle = LocalTextStyle.current.copy(
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = font.fontFamily,
                        textAlign = alignment.textAlign,
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
                Row(modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                    for (option in TextFont.entries) {
                        FilterChip(
                            selected = font == option,
                            onClick = { font = option },
                            label = { Text(option.label, fontFamily = option.fontFamily) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    for (option in TextAlignment.entries) {
                        FilterChip(
                            selected = alignment == option,
                            onClick = { alignment = option },
                            label = { Text(option.label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                // Only offered for a label that has a box (dragged out with the text tool):
                // switching it off turns the box back into a plain single-line label.
                if (pending.boxWidthWorld != null) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(
                            selected = keepBox,
                            onClick = { keepBox = !keepBox },
                            label = { Text("Wrap in text box") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(TextEditResult(text, bold, italic, sticky, sizePx, font, alignment, keepBox)) }) { Text("Done") }
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
