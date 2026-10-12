package com.andene.fern.canvas

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Playback speeds offered by tapping the fps label, the common hand-drawn animation rates.
private val FPS_CHOICES = listOf(6, 8, 12, 24)

/**
 * Flipbook controls shown in animation mode: step between frames, add a blank frame or a copy
 * of the current one, delete the frame, toggle onion skin, play/pause, and cycle playback speed.
 */
@Composable
fun AnimationBar(state: CanvasState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.goToAnimFrame(state.currentAnimFrame - 1) }, enabled = !state.playing && state.currentAnimFrame > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous frame")
            }
            Text("${state.currentAnimFrame + 1} / ${state.animFrameCount}", modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { state.goToAnimFrame(state.currentAnimFrame + 1) }, enabled = !state.playing && state.currentAnimFrame < state.animFrameCount - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next frame")
            }
            IconButton(onClick = { state.insertAnimFrame(copyCurrent = false) }, enabled = !state.playing) {
                Icon(Icons.Filled.AddBox, contentDescription = "Add blank frame after this one")
            }
            IconButton(onClick = { state.insertAnimFrame(copyCurrent = true) }, enabled = !state.playing) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate this frame")
            }
            IconButton(onClick = { state.deleteAnimFrame() }, enabled = !state.playing && state.animFrameCount > 1) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete this frame")
            }
            IconButton(onClick = { state.onionSkin = !state.onionSkin }) {
                Icon(
                    if (state.onionSkin) Icons.Filled.Layers else Icons.Filled.LayersClear,
                    contentDescription = if (state.onionSkin) "Onion skin: on" else "Onion skin: off",
                )
            }
            IconButton(onClick = { state.playing = !state.playing }, enabled = state.animFrameCount > 1) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                )
            }
            TextButton(onClick = {
                state.animFps = FPS_CHOICES[(FPS_CHOICES.indexOf(state.animFps) + 1) % FPS_CHOICES.size]
            }) {
                Text("${state.animFps} fps")
            }
        }
    }
}
