package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernSwitch
import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val PREFS_NAME = "onboarding"
private const val KEY_WELCOME_SEEN = "welcome_seen"

fun markWelcomeSeen(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
}

/**
 * Every tip in one list (design roadmap D29), from Settings: the ones shown in context, for
 * reading again; a switch brings in-context tips back after "No more tips"; and a practice page
 * to try things on without touching real work.
 */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit, onPractice: (() -> Unit)? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tipsOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!Tips.off(context)) }
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.welcome_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (tip in Tip.entries) {
                    Text("• " + stringResource(tip.text), modifier = Modifier.padding(vertical = FernScale.s4))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.tips_show_in_context), modifier = Modifier.weight(1f))
                    FernSwitch(checked = tipsOn, onCheckedChange = {
                        tipsOn = it
                        Tips.setOff(context, !it)
                    })
                }
            }
        },
        confirmButton = {
            FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.welcome_start)) }
        },
        dismissButton = {
            if (onPractice != null) FernTextButton(onClick = onPractice) { Text(stringResource(R.string.tips_practice)) }
        },
    )
}
