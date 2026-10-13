package com.andene.fern

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Job

/**
 * Long jobs - exports, imports - shown while they run, with a way to stop them (design roadmap
 * D31). The work stays in the background; drawing goes on meanwhile.
 */
object FernJobs {
    class Running(val label: String, val job: Job, val onCancel: () -> Unit)

    var current by mutableStateOf<Running?>(null)
        private set

    /** Shows [label] until [job] ends; Cancel stops it and runs [onCancel] (e.g. removes a half-written file). */
    fun track(label: String, job: Job, onCancel: () -> Unit = {}) {
        val running = Running(label, job, onCancel)
        current = running
        job.invokeOnCompletion { if (current === running) current = null }
    }

    fun cancel() {
        val running = current ?: return
        running.job.cancel()
        running.onCancel()
        current = null
    }
}

/** The running job's card: what it is, an indeterminate bar and Cancel. Nothing when idle. */
@Composable
fun FernJobHost(modifier: Modifier = Modifier) {
    val running = FernJobs.current ?: return
    Surface(modifier.widthIn(max = FernScale.s384).semantics { liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.large, tonalElevation = FernSpace.sm, shadowElevation = FernSpace.sm) {
        Column(Modifier.padding(start = FernSpace.lg, top = FernSpace.md, end = FernSpace.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(running.label, modifier = Modifier.weight(1f))
                TextButton(onClick = FernJobs::cancel) { Text(stringResource(R.string.action_cancel)) }
            }
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = FernSpace.sm, bottom = FernSpace.md))
        }
    }
}
