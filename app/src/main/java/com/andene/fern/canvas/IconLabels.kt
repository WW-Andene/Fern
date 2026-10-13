package com.andene.fern.canvas

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.andene.fern.FernScale
import com.andene.fern.FernSize

private const val KEY_ICON_LABELS = "icon_labels"

/**
 * Icon labels (design roadmap D95): a short name under each icon on the rail and in the More
 * panel, in the app's language, so a newcomer can read what an icon does. On by default (easy to
 * come in); experienced users switch them off in Settings for a quieter screen. The top bar's
 * few, universal icons (undo, redo, search, share) stay unlabelled to fit a phone.
 */
object IconLabels {
    private var loaded: MutableState<Boolean>? = null

    fun state(context: Context): MutableState<Boolean> =
        loaded ?: mutableStateOf(AppSettings.prefs(context).getBoolean(KEY_ICON_LABELS, true)).also { loaded = it }

    fun set(context: Context, shown: Boolean) {
        AppSettings.prefs(context).edit().putBoolean(KEY_ICON_LABELS, shown).apply()
        state(context).value = shown
    }
}

/** Whether icon labels are on, following a change made in Settings. */
@Composable
fun iconLabelsShown(): Boolean {
    val shown by IconLabels.state(LocalContext.current)
    return shown
}

/**
 * An icon button that shows [label] under its icon while icon labels are on (and as its
 * accessibility label always). Without labels it is Material's 48 dp icon button.
 */
@Composable
fun LabelledIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    if (!iconLabelsShown()) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) { Icon(icon, contentDescription = label, tint = tint) }
        return
    }
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Column(
        modifier = modifier
            .heightIn(min = FernSize.touchTarget)
            .widthIn(min = FernSize.touchTarget, max = FernScale.s64)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = tint.alpha * alpha))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = LocalContentColor.current.copy(alpha = alpha),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
