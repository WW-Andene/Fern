package com.andene.fern.canvas

import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.andene.fern.R
import java.text.DateFormat
import java.util.Date

/**
 * The document's statistics (roadmap item 132): time worked on it and how much was drawn
 * ([DocumentStats], counted on this device), and what it holds now.
 */
@Composable
fun StatsDialog(state: CanvasState, onDismiss: () -> Unit) {
    val stats = state.stats
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val rows = listOf(
        R.string.stats_active_time to formatDuration(stats.activeMillis),
        R.string.stats_strokes_drawn to stats.strokesDrawn.toString(),
        R.string.stats_erases to stats.erases.toString(),
        R.string.stats_first to (if (stats.firstAction > 0) format.format(Date(stats.firstAction)) else "–"),
        R.string.stats_last to (if (stats.lastAction > 0) format.format(Date(stats.lastAction)) else "–"),
        R.string.stats_strokes_now to state.strokes.size.toString(),
        R.string.stats_points_now to state.strokes.sumOf { it.points.size.toLong() }.toString(),
        R.string.stats_colours to state.strokes.mapTo(HashSet()) { it.color.copy(alpha = 1f) }.size.toString(),
        R.string.stats_layers to state.layers.size.toString(),
        R.string.stats_frames to state.animFrameCount.toString(),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_title)) },
        text = {
            Column {
                for ((label, value) in rows) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s4)) {
                        Text(stringResource(label), modifier = Modifier.weight(1f))
                        Text(value, fontWeight = FontWeight.Bold)
                    }
                }
                Text(stringResource(R.string.stats_note), modifier = Modifier.padding(top = FernScale.s8))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** [millis] as hours and minutes ("2 h 05 min"), or minutes alone under an hour. */
internal fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h %02d min".format(minutes % 60)
}
