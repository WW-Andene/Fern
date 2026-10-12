package com.andene.fern

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * Docked panels (design roadmap D10): the panels that used to be modal dialogs - layers,
 * frames, symbols, pins, versions, objects and their logic - open in a panel that leaves the
 * canvas usable. On a phone held upright it's a bottom sheet taking at most half the screen,
 * that collapses to its header; otherwise it docks beside the rail. Panels stack: one opened
 * from another (an object's rules) shows on top, and closing it brings the other back.
 */

/** One open panel. */
class PanelEntry internal constructor(
    val key: Any,
    title: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    var title by mutableStateOf(title)
    var onDismissRequest by mutableStateOf(onDismissRequest)
    var content by mutableStateOf(content)
    var actions by mutableStateOf(actions)
}

/** The open panels, last on top. */
object FernPanels {
    val open = mutableStateListOf<PanelEntry>()
}

/**
 * A panel, called where a dialog would be: shown by the [FernPanelHost] for as long as this is
 * in the composition. Same parameters as an `AlertDialog`; [confirmButton] and [dismissButton]
 * sit at the panel's foot.
 */
@Composable
fun FernPanel(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
) {
    val key = remember { Any() }
    val actions: @Composable () -> Unit = {
        dismissButton()
        confirmButton()
    }
    DisposableEffect(key) {
        FernPanels.open.add(PanelEntry(key, title, onDismissRequest, text, actions))
        onDispose { FernPanels.open.removeAll { it.key === key } }
    }
    SideEffect {
        FernPanels.open.firstOrNull { it.key === key }?.let {
            it.title = title
            it.onDismissRequest = onDismissRequest
            it.content = text
            it.actions = actions
        }
    }
}

/** Shows the top panel; place once, in the screen's root box. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FernPanelHost(railOnLeft: Boolean, railInset: androidx.compose.ui.unit.Dp, railSide: androidx.compose.ui.unit.Dp) {
    val entry = FernPanels.open.lastOrNull() ?: return
    var collapsed by remember(entry.key) { mutableStateOf(false) }
    BackHandler { entry.onDismissRequest() }
    val configuration = LocalConfiguration.current
    val portrait = configuration.screenHeightDp >= configuration.screenWidthDp
    val modifier = if (portrait) {
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = railInset)
            .heightIn(max = (configuration.screenHeightDp / 2).dp)
    } else {
        Modifier
            .align(if (railOnLeft) androidx.compose.ui.AbsoluteAlignment.CenterLeft else androidx.compose.ui.AbsoluteAlignment.CenterRight)
            .padding(horizontal = railSide)
            .width(FernScale.s384)
            .heightIn(max = configuration.screenHeightDp.dp - FernScale.s32)
    }
    Surface(modifier.padding(FernSpace.sm), shape = MaterialTheme.shapes.large, tonalElevation = FernSpace.xs, shadowElevation = FernSpace.sm) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = FernSpace.lg), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).semantics { heading() }) {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) { entry.title() }
                }
                IconButton(onClick = { collapsed = !collapsed }) {
                    Icon(
                        if (collapsed) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(if (collapsed) R.string.panel_expand else R.string.panel_collapse),
                    )
                }
                IconButton(onClick = { entry.onDismissRequest() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            if (!collapsed) {
                HorizontalDivider()
                Box(Modifier.weight(1f, fill = false).padding(horizontal = FernSpace.lg, vertical = FernSpace.sm)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { entry.content() }
                    }
                }
                // Actions scroll sideways rather than squeeze on a phone (the layers panel has several).
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = FernSpace.sm), horizontalArrangement = Arrangement.End) { entry.actions() }
            }
        }
    }
}
