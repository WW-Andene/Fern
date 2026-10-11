package com.andene.fern.canvas

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
import androidx.compose.ui.unit.dp
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
        title = { Text("Version history") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 384.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (versions.isEmpty()) {
                    Text("No versions yet. One is saved every 10 minutes while you draw.")
                }
                for (timestamp in versions) {
                    Text(
                        text = format.format(Date(timestamp)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRestore(timestamp) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
