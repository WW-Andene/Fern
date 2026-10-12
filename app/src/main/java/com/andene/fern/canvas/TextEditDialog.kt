package com.andene.fern.canvas

import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

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
    val link: LinkTarget?,
    val outlined: Boolean = false,
    val features: Set<OpenTypeFeature> = emptySet(),
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
    linkChoices: LinkChoices = LinkChoices(),
    /** Saves the edit, then goes to the link's target. */
    onFollowLink: (LinkTarget) -> Unit = {},
) {
    var text by remember { mutableStateOf(pending.initialText) }
    var bold by remember { mutableStateOf(pending.initialBold) }
    var italic by remember { mutableStateOf(pending.initialItalic) }
    var sticky by remember { mutableStateOf(pending.initialSticky) }
    var outlined by remember { mutableStateOf(pending.initialOutlined) }
    var features by remember { mutableStateOf(pending.initialFeatures) }
    // Snap to the nearest offered size, so an existing label shows its closest size selected.
    var sizePx by remember { mutableStateOf(TEXT_SIZES_PX.minBy { kotlin.math.abs(it - pending.initialSizePx) }) }
    var font by remember { mutableStateOf(pending.initialFont) }
    var alignment by remember { mutableStateOf(pending.initialAlignment) }
    var keepBox by remember { mutableStateOf(true) }
    var link by remember { mutableStateOf(pending.initialLink) }
    var linkMenu by remember { mutableStateOf<LinkMenu?>(null) }
    fun result() = TextEditResult(text, bold, italic, sticky, sizePx, font, alignment, keepBox, link, outlined, features)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pending.initialText.isEmpty()) stringResource(R.string.text_add) else stringResource(R.string.text_edit)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.text_label)) },
                    textStyle = LocalTextStyle.current.copy(
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = font.fontFamily,
                        textAlign = alignment.textAlign,
                        fontFeatureSettings = featureSettings(features),
                    ),
                )
                // OpenType features (roadmap item 98).
                Row(modifier = Modifier.padding(top = FernScale.s8).horizontalScroll(rememberScrollState())) {
                    for (feature in OpenTypeFeature.entries) {
                        FilterChip(
                            selected = feature in features,
                            onClick = { features = if (feature in features) features - feature else features + feature },
                            label = { Text(stringResource(feature.label)) },
                            modifier = Modifier.padding(end = FernScale.s8),
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = FernScale.s8)) {
                    FilterChip(
                        selected = bold,
                        onClick = { bold = !bold },
                        label = { Text(stringResource(R.string.text_bold), fontWeight = FontWeight.Bold) },
                        modifier = Modifier.padding(end = FernScale.s8),
                    )
                    FilterChip(
                        selected = italic,
                        onClick = { italic = !italic },
                        label = { Text(stringResource(R.string.text_italic), fontStyle = FontStyle.Italic) },
                        modifier = Modifier.padding(end = FernScale.s8),
                    )
                    FilterChip(
                        selected = sticky,
                        onClick = { sticky = !sticky },
                        label = { Text(stringResource(R.string.text_sticky)) },
                        modifier = Modifier.padding(end = FernScale.s8),
                    )
                    FilterChip(
                        selected = outlined,
                        onClick = { outlined = !outlined },
                        label = { Text(stringResource(R.string.text_outlined)) },
                    )
                }
                Row(modifier = Modifier.padding(top = FernScale.s8)) {
                    for ((size, label) in TEXT_SIZES_PX.zip(listOf("S", "M", "L", "XL"))) {
                        FilterChip(
                            selected = sizePx == size,
                            onClick = { sizePx = size },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = FernScale.s8),
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = FernScale.s8).horizontalScroll(rememberScrollState())) {
                    for (option in TextFont.entries) {
                        FilterChip(
                            selected = font == option,
                            onClick = { font = option },
                            label = { Text(stringResource(option.label), fontFamily = option.fontFamily) },
                            modifier = Modifier.padding(end = FernScale.s8),
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = FernScale.s8)) {
                    for (option in TextAlignment.entries) {
                        FilterChip(
                            selected = alignment == option,
                            onClick = { alignment = option },
                            label = { Text(stringResource(option.label)) },
                            modifier = Modifier.padding(end = FernScale.s8),
                        )
                    }
                }
                // Link to a pin (a place on this canvas) or to another document.
                Row(modifier = Modifier.padding(top = FernScale.s8), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = link == null, onClick = { link = null }, label = { Text(stringResource(R.string.text_no_link)) }, modifier = Modifier.padding(end = FernScale.s8))
                    Box {
                        FilterChip(
                            selected = link is LinkTarget.Pin,
                            onClick = { linkMenu = LinkMenu.PINS },
                            enabled = linkChoices.pins.isNotEmpty(),
                            label = { Text(stringResource(R.string.text_link_pin)) },
                            modifier = Modifier.padding(end = FernScale.s8),
                        )
                        DropdownMenu(expanded = linkMenu == LinkMenu.PINS, onDismissRequest = { linkMenu = null }) {
                            for (pin in linkChoices.pins) {
                                DropdownMenuItem(text = { Text(pin.name) }, onClick = {
                                    link = LinkTarget.Pin(pin.id)
                                    linkMenu = null
                                })
                            }
                        }
                    }
                    Box {
                        FilterChip(
                            selected = link is LinkTarget.Document,
                            onClick = { linkMenu = LinkMenu.DOCUMENTS },
                            enabled = linkChoices.documents.isNotEmpty(),
                            label = { Text(stringResource(R.string.text_link_document)) },
                        )
                        DropdownMenu(expanded = linkMenu == LinkMenu.DOCUMENTS, onDismissRequest = { linkMenu = null }) {
                            for (document in linkChoices.documents) {
                                DropdownMenuItem(text = { Text(document.name) }, onClick = {
                                    link = LinkTarget.Document(document.id)
                                    linkMenu = null
                                })
                            }
                        }
                    }
                }
                link?.let { target ->
                    val name = when (target) {
                        is LinkTarget.Pin -> linkChoices.pins.firstOrNull { it.id == target.pinId }?.name
                        is LinkTarget.Document -> linkChoices.documents.firstOrNull { it.id == target.documentId }?.name
                    }
                    Text(
                        if (name != null) stringResource(R.string.text_links_to, name) else stringResource(R.string.text_link_missing),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = FernScale.s4),
                    )
                }
                // Only offered for a label that has a box (dragged out with the text tool):
                // switching it off turns the box back into a plain single-line label.
                if (pending.boxWidthWorld != null) {
                    Row(modifier = Modifier.padding(top = FernScale.s8)) {
                        FilterChip(
                            selected = keepBox,
                            onClick = { keepBox = !keepBox },
                            label = { Text(stringResource(R.string.text_wrap)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(result()) }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            Row {
                // A label holding a web address works as a link on the board: open it from here.
                val webLink = remember(text) { firstWebLink(text) }
                val uriHandler = LocalUriHandler.current
                if (webLink != null) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(webLink) } }) { Text(stringResource(R.string.text_open_link)) }
                }
                link?.let { target ->
                    TextButton(onClick = {
                        onConfirm(result())
                        onFollowLink(target)
                    }) { Text(stringResource(R.string.text_follow_link)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private enum class LinkMenu { PINS, DOCUMENTS }

// Text sizes offered, in screen px at the zoom the label is placed at; all on the §7 scale.
private val TEXT_SIZES_PX = listOf(16.0, 24.0, 32.0, 48.0)

/** The first web address in [text], with https:// added when it was typed without a scheme; null if none. */
private fun firstWebLink(text: String): String? {
    val matcher = android.util.Patterns.WEB_URL.matcher(text)
    if (!matcher.find()) return null
    val url = matcher.group()
    return if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) url else "https://$url"
}
