package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * Finds typed text labels in the current document; tapping a result centres it on screen.
 * Handwriting isn't searchable until handwriting recognition exists (roadmap item 67).
 */
@Composable
fun SearchDialog(textItems: List<TextItem>, onJump: (TextItem) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = if (query.isBlank()) emptyList() else textItems.filter { it.text.contains(query.trim(), ignoreCase = true) }

    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_title)) },
        text = {
            Column {
                FernTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = FernScale.s384)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (query.isNotBlank() && matches.isEmpty()) {
                        Text(stringResource(R.string.search_no_matches), modifier = Modifier.padding(vertical = FernScale.s8))
                    }
                    for (item in matches) {
                        Text(
                            text = item.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJump(item) }
                                .padding(vertical = FernScale.s12),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
