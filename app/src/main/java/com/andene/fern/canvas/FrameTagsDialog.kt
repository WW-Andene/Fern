package com.andene.fern.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andene.fern.R
import kotlin.math.roundToInt

/**
 * Frame tags (roadmap item 145): the animation's named frame ranges, each with its colour and
 * direction - play one on its own (looping its frames), or delete it - and a form to add one
 * (re-using a name replaces that tag). Starts on the current frame.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrameTagsDialog(state: CanvasState, onDismiss: () -> Unit) {
    val last = state.animFrameCount - 1
    var name by remember { mutableStateOf("") }
    var from by remember { mutableFloatStateOf(state.currentAnimFrame.toFloat()) }
    var to by remember { mutableFloatStateOf(state.currentAnimFrame.toFloat()) }
    var color by remember { mutableIntStateOf(TAG_COLORS[state.frameTags.size % TAG_COLORS.size]) }
    var direction by remember { mutableStateOf(TagDirection.FORWARD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            Column(Modifier.heightIn(max = 512.dp).verticalScroll(rememberScrollState())) {
                if (state.frameTags.isEmpty()) Text(stringResource(R.string.tags_empty))
                for (tag in state.frameTags) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(Color(tag.color), CircleShape))
                        Text(
                            stringResource(R.string.tags_row, tag.name, tag.from + 1, tag.to + 1, stringResource(directionLabel(tag.direction))),
                            modifier = Modifier.weight(1f).padding(start = 8.dp).clickable { state.goToAnimFrame(tag.from) },
                        )
                        IconButton(onClick = {
                            state.playTag(tag.name)
                            onDismiss()
                        }) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.tags_play)) }
                        IconButton(onClick = { state.removeFrameTag(tag.name) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tags_delete)) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.tags_name)) }, singleLine = true)
                if (last > 0) {
                    Text(stringResource(R.string.tags_from, from.roundToInt() + 1))
                    Slider(value = from, onValueChange = { from = it.roundToInt().toFloat(); if (to < from) to = from }, valueRange = 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0))
                    Text(stringResource(R.string.tags_to, to.roundToInt() + 1))
                    Slider(value = to, onValueChange = { to = it.roundToInt().toFloat(); if (from > to) from = to }, valueRange = 0f..last.toFloat(), steps = (last - 1).coerceAtLeast(0))
                }
                Row {
                    for (option in TAG_COLORS) {
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(if (option == color) 32.dp else 24.dp)
                                .background(Color(option), CircleShape)
                                .clickable { color = option },
                        )
                    }
                }
                FlowRow {
                    for (option in TagDirection.entries) {
                        FilterChip(selected = direction == option, onClick = { direction = option }, label = { Text(stringResource(directionLabel(option))) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
                TextButton(onClick = {
                    state.putFrameTag(FrameTag(name.trim(), from.roundToInt(), to.roundToInt(), color, direction))
                    name = ""
                }, enabled = name.isNotBlank()) { Text(stringResource(R.string.tags_add)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/**
 * The onion skin (roadmap item 145): how many frames before and after the current one show,
 * and whether they're tinted red and blue.
 */
@Composable
fun OnionSkinDialog(state: CanvasState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onion_title)) },
        text = {
            Column {
                Text(stringResource(R.string.onion_before, state.onionBefore))
                Slider(value = state.onionBefore.toFloat(), onValueChange = { state.onionBefore = it.roundToInt() }, valueRange = 0f..MAX_ONION_FRAMES.toFloat(), steps = MAX_ONION_FRAMES - 1)
                Text(stringResource(R.string.onion_after, state.onionAfter))
                Slider(value = state.onionAfter.toFloat(), onValueChange = { state.onionAfter = it.roundToInt() }, valueRange = 0f..MAX_ONION_FRAMES.toFloat(), steps = MAX_ONION_FRAMES - 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.onion_tinted), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.onionTinted, onCheckedChange = { state.onionTinted = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun directionLabel(direction: TagDirection) = when (direction) {
    TagDirection.FORWARD -> R.string.tags_forward
    TagDirection.REVERSE -> R.string.tags_reverse
    TagDirection.PING_PONG -> R.string.tags_pingpong
}

private const val MAX_ONION_FRAMES = 4
