package com.andene.fern

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier

/**
 * Advanced settings one tap away in the same place (design roadmap D32): a "More" button that
 * opens [content] in place, never a separate dialog. [key] keeps it open or closed across
 * recompositions and rotations. It starts open in expert density ([FernDensity]).
 */
@Composable
fun MoreOptions(key: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(FernDensity.expert) }
    val state = stringResource(if (open) R.string.more_open else R.string.more_closed)
    FernTextButton(onClick = { open = !open }, modifier = Modifier.semantics { stateDescription = state }) {
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        Text(stringResource(if (open) R.string.more_less else R.string.more_more))
    }
    if (open) Column { content() }
}

/** The same, for a row of buttons: "More" then the extra buttons inline after it; open from the start in expert density. */
@Composable
fun MoreInline(key: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(FernDensity.expert) }
    val state = stringResource(if (open) R.string.more_open else R.string.more_closed)
    FernTextButton(onClick = { open = !open }, modifier = Modifier.semantics { stateDescription = state }) {
        Text(stringResource(if (open) R.string.more_less else R.string.more_more))
    }
    if (open) content()
}
