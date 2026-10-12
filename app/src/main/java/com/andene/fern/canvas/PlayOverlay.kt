package com.andene.fern.canvas

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * The game playing (roadmap items 155 and 161): steps the world once per display frame, keeps
 * touches off the drawing underneath, and offers restart and stop. Editing chrome is hidden
 * while it shows.
 */
@Composable
fun PlayOverlay(state: CanvasState, modifier: Modifier = Modifier) {
    val world = state.play
    LaunchedEffect(world) {
        if (world == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (state.play === world) {
            withFrameNanos { now ->
                // A long pause (the app in the background) doesn't jump the game ahead.
                state.stepPlay(((now - last) / 1e9).coerceIn(0.0, MAX_STEP_SECONDS))
                last = now
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitEachGesture { while (true) { val event = awaitPointerEvent(); event.changes.forEach { it.consume() }; if (event.changes.none { it.pressed }) break } } },
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            Row {
                IconButton(onClick = { state.stopPlay(); state.startPlay() }) { Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.play_restart)) }
                IconButton(onClick = { state.stopPlay() }) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.play_stop)) }
            }
        }
    }
}

// The longest step the world takes at once, in seconds.
private const val MAX_STEP_SECONDS = 0.05
