package com.andene.fern

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

private const val PREFS = "confirmations"

/** Whether the user said not to be asked again about [key]. */
fun confirmationSkipped(context: Context, key: String): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false)

/**
 * Asks once before an action undo can't take back (design roadmap D26), with "Don't ask again".
 * Callers check [confirmationSkipped] first and act straight away when it's true.
 */
@Composable
fun ConfirmIrreversible(key: String, message: String, actionLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var dontAsk by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                Text(message)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text(stringResource(R.string.confirm_dont_ask))
                }
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                if (dontAsk) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, true).apply()
                onConfirm()
            }) { Text(actionLabel) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
