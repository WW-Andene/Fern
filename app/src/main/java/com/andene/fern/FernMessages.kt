package com.andene.fern

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Short messages to the user (design roadmap D6), shown as snackbars instead of toasts: they can
 * carry an action ("Undo"), TalkBack reads them as they appear, and [FernMessageHost] places them
 * away from the drawing hand. Any code can [show] one; the host on screen displays them in turn.
 */
object FernMessages {
    class Message(val text: String, val actionLabel: String?, val onAction: (() -> Unit)?)

    private val queue = Channel<Message>(Channel.BUFFERED)

    /** Shows [text]; with [actionLabel], a button that runs [onAction]. Safe from any thread. */
    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        queue.trySend(Message(text, actionLabel, onAction))
    }

    internal suspend fun next(): Message = queue.receive()
}

/** Where [FernMessages] appear; one per screen. */
@Composable
fun FernMessageHost(modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(host) {
        while (true) {
            val message = FernMessages.next()
            // Each message waits for the previous one, but a new one doesn't block the caller.
            launch {
                val result = host.showSnackbar(
                    message.text,
                    actionLabel = message.actionLabel,
                    withDismissAction = message.actionLabel != null,
                    duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            }
        }
    }
    SnackbarHost(host, modifier)
}
