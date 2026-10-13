package com.andene.fern.canvas

import com.andene.fern.FernSlider
import com.andene.fern.FernColors

import com.andene.fern.FernScale

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlinx.coroutines.delay
import java.io.File

/**
 * The on-canvas bar for voice notes (roadmap item 130). While recording: the time so far and a
 * stop button, so the user can draw while they talk. While [playing] a note: play/pause, a seek
 * slider and close; the ink drawn during the recording replays in step with it (see
 * [AudioReplay]), and [AudioSeekOverlay] lets a tap on that ink jump to when it was drawn.
 */
@Composable
fun AudioBar(audio: AudioNotes, state: CanvasState, playing: File?, onClosePlayback: () -> Unit, modifier: Modifier = Modifier) {
    val since = audio.recordingSince
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (since != null) {
        LaunchedEffect(since) {
            while (true) {
                now = System.currentTimeMillis()
                delay(BAR_TICK_MILLIS)
            }
        }
        Surface(modifier = modifier, shape = MaterialTheme.shapes.large, tonalElevation = FernScale.s4, shadowElevation = FernScale.s4) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = FernScale.s12)) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = RECORDING_COLOR)
                Text(stringResource(R.string.audio_recording_for, clock(now - since)), modifier = Modifier.padding(start = FernScale.s8))
                IconButton(onClick = { audio.stop() }) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.audio_stop_recording))
                }
            }
        }
        return
    }
    if (playing == null) return
    val start = recordingStart(playing) ?: return // not a note file name; nothing to sync with
    var paused by remember(playing) { mutableStateOf(false) }
    var position by remember(playing) { mutableLongStateOf(0L) }
    var duration by remember(playing) { mutableLongStateOf(0L) }
    DisposableEffect(playing) {
        audio.play(playing) { onClosePlayback() }
        onDispose {
            audio.stopPlayback()
            state.audioReplay = null
        }
    }
    LaunchedEffect(playing) {
        while (true) {
            position = audio.playbackPosition
            duration = audio.playbackDuration
            if (duration > 0) state.audioReplay = AudioReplay(start, start + duration, position)
            delay(BAR_TICK_MILLIS)
        }
    }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, tonalElevation = FernScale.s4, shadowElevation = FernScale.s4) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (paused) audio.resume() else audio.pause()
                paused = !paused
            }) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (paused) R.string.action_play else R.string.audio_pause),
                )
            }
            FernSlider(
                value = if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { fraction ->
                    position = (fraction * duration).toLong()
                    audio.seekTo(position)
                },
                modifier = Modifier.width(FernScale.s192),
            )
            Text(clock(position), modifier = Modifier.padding(horizontal = FernScale.s8))
            IconButton(onClick = onClosePlayback) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.audio_close_playback))
            }
        }
    }
}

/**
 * Over the canvas while a voice note plays in step with its ink: a tap on ink drawn during the
 * recording jumps playback to the moment it was drawn. It takes the canvas's touches while
 * playback is open (listening, not drawing); closing playback gives them back.
 */
@Composable
fun AudioSeekOverlay(audio: AudioNotes, state: CanvasState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.pointerInput(state) {
            detectTapGestures { tap ->
                val replay = state.audioReplay ?: return@detectTapGestures
                val center = Offset(size.width / 2f, size.height / 2f)
                val world = state.screenToWorld(state.unrotateScreen(tap, center), center)
                state.syncedStrokeAt(world)?.let { stroke -> replay.positionOf(stroke)?.let(audio::seekTo) }
            }
        },
    )
}

private fun clock(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private const val BAR_TICK_MILLIS = 64L
private val RECORDING_COLOR: Color get() = FernColors.current.danger
