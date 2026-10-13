package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andene.fern.R

/*
 * A handle's value on the canvas (roadmap item 172): read out beside it while it's dragged, and
 * typed in when it's held. Every handle has one ([typedValue]): its position by default, or what
 * it really sets - the selection's size, its turn.
 */

/** "X 120 px · Y 48 px": [value]'s fields as they are now, for the readout beside a dragged handle. */
@Composable
internal fun handleReadout(value: HandleValue): String =
    value.fields.joinToString(" · ") { field -> "${stringResource(field.label)} ${formatMeasure(field.read())} ${field.unit}".trimEnd() }

/**
 * Typing a held handle's value (roadmap item 172): one field per number, filled with the value as
 * it is; Apply sets them all at once, as one undo step where the handle's change is undoable. A
 * field that doesn't read as a number keeps its value.
 */
@Composable
internal fun HandleEntryPanel(value: HandleValue, onDismiss: () -> Unit) {
    val texts = remember(value) { mutableStateListOf<String>().apply { addAll(value.fields.map { formatMeasure(it.read()) }) } }
    // Accepts either decimal separator, whatever the keyboard offers.
    fun parse(text: String) = text.trim().replace(',', '.').toDoubleOrNull()
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.handle_entry_title)) },
        text = {
            Column {
                value.fields.forEachIndexed { i, field ->
                    FernTextField(
                        texts[i],
                        { texts[i] = it },
                        label = { Text("${stringResource(field.label)} (${field.unit})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(bottom = FernScale.s8),
                    )
                }
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                value.apply(value.fields.mapIndexed { i, field -> parse(texts[i]) ?: field.read() })
                onDismiss()
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
