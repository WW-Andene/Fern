package com.andene.fern.canvas

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
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
    R.string.welcome_tip_navigate,
    R.string.welcome_tip_undo,
    R.string.welcome_tip_shapes,
    R.string.welcome_tip_eyedropper,
    R.string.welcome_tip_erase,
    R.string.welcome_tip_toolbar,
)

/** First-launch guide to the gestures; dismissing it marks it seen so it doesn't return. */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.welcome_title)) },
        text = {
            Column {
                for (tip in tips) {
                    Text("• " + stringResource(tip), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.welcome_start)) }
        },
    )
}
