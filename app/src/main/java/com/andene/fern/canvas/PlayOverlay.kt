package com.andene.fern.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * The game playing (roadmap items 155, 160 and 161): steps the world once per display frame and
 * feeds it the player's input - keys and gamepad buttons (the screen keeps the focus), the
 * on-screen buttons the game's actions use, and fingers and the stylus on the game itself
 * (gamepad sticks arrive through the activity, see [GameInputState.axis]). Restart and stop sit
 * at the top. Editing chrome is hidden while it shows.
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                val input = state.play?.input ?: return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> input.key(event.key.nativeKeyCode, true)
                    KeyEventType.KeyUp -> input.key(event.key.nativeKeyCode, false)
                    else -> return@onKeyEvent false
                }
                true
            }
            // Fingers and the stylus on the game: where, in world space, while down.
            .pointerInput(world) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    fun at(p: Offset) = state.screenToWorld(state.unrotateScreen(p, Offset(size.width / 2f, size.height / 2f)), Offset(size.width / 2f, size.height / 2f))
                    world?.input?.pointerAt(at(down.position), stylus)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        world?.input?.pointerAt(at(change.position), stylus)
                    }
                    world?.input?.pointerAt(null, stylus)
                }
            },
    ) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp)) {
            Row {
                IconButton(onClick = { state.playPaused = !state.playPaused }) {
                    Icon(
                        if (state.playPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(if (state.playPaused) R.string.play_resume else R.string.play_pause),
                    )
                }
                IconButton(onClick = { state.restartPlay() }) { Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.play_restart)) }
                IconButton(onClick = { state.stopPlay() }) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.play_stop)) }
            }
        }
        // The heads-up display (roadmap item 161): the properties of objects that show them.
        state.playRevision // refresh with every step
        val shown = world?.instances?.filter { it.def.showInHud && !it.removed }.orEmpty()
        if (shown.isNotEmpty()) {
            Column(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp).background(HUD_COLOR, MaterialTheme.shapes.medium).padding(8.dp)) {
                for (instance in shown) for ((name, value) in instance.properties.toSortedMap()) {
                    Text("${instance.def.name} $name: ${plainNumber(value)}", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        val buttons = world?.input?.touchButtons.orEmpty()
        // A d-pad bottom left, the A and B buttons bottom right.
        Column(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.systemBars).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (TouchButton.UP in buttons) PadButton(state, TouchButton.UP, "▲")
            Row {
                if (TouchButton.LEFT in buttons) PadButton(state, TouchButton.LEFT, "◀")
                if (TouchButton.LEFT in buttons || TouchButton.RIGHT in buttons) Box(Modifier.size(PAD_DP.dp))
                if (TouchButton.RIGHT in buttons) PadButton(state, TouchButton.RIGHT, "▶")
            }
            if (TouchButton.DOWN in buttons) PadButton(state, TouchButton.DOWN, "▼")
        }
        Row(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.systemBars).padding(24.dp)) {
            if (TouchButton.B in buttons) PadButton(state, TouchButton.B, "B")
            if (TouchButton.A in buttons) PadButton(state, TouchButton.A, "A")
        }
    }
}

/** One on-screen button: held while a finger is on it. */
@Composable
private fun PadButton(state: CanvasState, button: TouchButton, label: String) {
    Box(
        Modifier
            .padding(4.dp)
            .size(PAD_DP.dp)
            .background(PAD_COLOR, CircleShape)
            .pointerInput(state.play) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    state.play?.input?.touchButton(button, true)
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                    state.play?.input?.touchButton(button, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White) }
}

// The longest step the world takes at once, in seconds.
private const val MAX_STEP_SECONDS = 0.05
private const val PAD_DP = 64
private val PAD_COLOR = Color(0x66000000)
private val HUD_COLOR = Color(0x99000000)
