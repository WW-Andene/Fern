package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernTextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.FernSpace
import com.andene.fern.R

/**
 * Navigation one tap away (design roadmap D41): fit the drawing, 100 %, level the turn (only
 * while turned), back to where I was (after a jump), and the minimap on or off.
 */
@Composable
fun NavigationAids(state: CanvasState, minimapShown: Boolean, onMinimapChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = FernSpace.xs, shadowElevation = FernSpace.xs) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.fitWithReturn() }) { Icon(FernIcons.CenterFocusStrong, contentDescription = stringResource(R.string.gesture_action_fit)) }
            FernTextButton(onClick = { state.actualSize() }) { Text(stringResource(R.string.nav_actual_size)) }
            if (state.rotation != 0.0) IconButton(onClick = { state.levelView() }) { Icon(FernIcons.Explore, contentDescription = stringResource(R.string.nav_level)) }
            if (state.previousView != null) IconButton(onClick = { state.backToPreviousView() }) { Icon(FernIcons.Undo, contentDescription = stringResource(R.string.nav_back)) }
            IconButton(onClick = { onMinimapChange(!minimapShown) }) {
                Icon(FernIcons.Map, contentDescription = stringResource(if (minimapShown) R.string.nav_minimap_hide else R.string.nav_minimap_show))
            }
        }
    }
}
