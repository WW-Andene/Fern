package com.andene.fern.canvas

import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.andene.fern.R

/**
 * The QR code tool (roadmap item 144): text or a link, how much damage the code should survive,
 * and whether it gets a white backing square; Insert places it as a vector group in the
 * active colour at the centre of the view (undoable). Text too long for a QR code says so.
 */
@Composable
fun QrCodeDialog(state: CanvasState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var correction by remember { mutableStateOf(QrErrorCorrection.MEDIUM) }
    var lightBackground by remember { mutableStateOf(true) }
    var tooLong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_title)) },
        text = {
            Column {
                OutlinedTextField(
                    text,
                    {
                        text = it
                        tooLong = false
                    },
                    label = { Text(stringResource(R.string.qr_text)) },
                    isError = tooLong,
                    supportingText = if (tooLong) ({ Text(stringResource(R.string.qr_too_long)) }) else null,
                )
                Text(stringResource(R.string.qr_correction))
                Row {
                    for ((option, label) in listOf(
                        QrErrorCorrection.LOW to "7 %", QrErrorCorrection.MEDIUM to "15 %",
                        QrErrorCorrection.QUARTILE to "25 %", QrErrorCorrection.HIGH to "30 %",
                    )) {
                        FilterChip(selected = correction == option, onClick = { correction = option }, label = { Text(label) }, modifier = Modifier.padding(end = FernScale.s4))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = lightBackground, onCheckedChange = { lightBackground = it })
                    Text(stringResource(R.string.qr_background), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val qr = qrOutlines(text, correction)
                if (qr == null) {
                    tooLong = true
                    return@TextButton
                }
                state.insertQrCode(qr, lightBackground)
                onDismiss()
            }, enabled = text.isNotEmpty()) { Text(stringResource(R.string.qr_insert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
