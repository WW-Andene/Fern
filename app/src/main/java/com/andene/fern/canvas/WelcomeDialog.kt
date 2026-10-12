package com.andene.fern.canvas

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "onboarding"
private const val KEY_WELCOME_SEEN = "welcome_seen"

/** True until the welcome guide has been dismissed once on this install. */
fun shouldShowWelcome(context: Context): Boolean =
    !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_WELCOME_SEEN, false)

fun markWelcomeSeen(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
}

// The gestures aren't discoverable from the toolbar, so they're the part worth teaching.
private val tips = listOf(
    "One finger draws; two fingers pan, pinch to zoom and twist to rotate. The sheet never ends.",
    "Tap with two fingers to undo, three to redo.",
    "Hold the pen still at the end of a stroke to snap it into a clean line, box or circle.",
    "Touch and hold without moving to pick up the colour underneath.",
    "Flip your stylus or hold its button to erase.",
    "Scroll the toolbar sideways for more tools; the ⋮ menu holds images, PDFs, frames, pins and settings.",
)

/** First-launch guide to the gestures; dismissing it marks it seen so it doesn't return. */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to Fern") },
        text = {
            Column {
                for (tip in tips) {
                    Text("• $tip", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Start drawing") }
        },
    )
}
