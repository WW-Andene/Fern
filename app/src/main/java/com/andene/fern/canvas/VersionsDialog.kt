package com.andene.fern.canvas

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.text.DateFormat
import java.util.Date

/**
 * The current document's version timeline (see [CanvasStorage.listVersions]). Tapping a
 * version restores it; the restore is itself undoable, so picking the wrong one is harmless.
 */
@Composable
fun VersionsDialog(
    versions: List<Long>,
    onRestore: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.versions_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = FernScale.s384)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (versions.isEmpty()) {
                    Text(stringResource(R.string.versions_empty))
                }
                for (timestamp in versions) {
                    Text(
                        text = format.format(Date(timestamp)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRestore(timestamp) }
                            .padding(vertical = FernScale.s12),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
