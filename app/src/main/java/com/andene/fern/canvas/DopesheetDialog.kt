package com.andene.fern.canvas

import com.andene.fern.FernPanel

import com.andene.fern.FernScale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andene.fern.R
import kotlin.math.PI

/**
 * Dopesheet and curve editor for the skeleton (roadmap item 134, after Spine's). The dopesheet
 * is a row per bone and a column per frame: ● where the bone's pose changes from the frame
 * before (a key), – where it holds. Tapping a cell goes to that frame and selects the bone.
 * Below, the selected bone's curve: its angle (to its parent, or its own for a root) frame by
 * frame; dragging a frame's point up or down turns the bone in that frame, children and ink
 * with it. Hold makes the current frame repeat the frame before for that bone.
 */
@Composable
fun DopesheetDialog(state: CanvasState, onDismiss: () -> Unit) {
    val boneLabel = stringResource(R.string.dopesheet_bone)
    val tracks = state.boneTimeline { n -> "$boneLabel $n" }
    var selectedId by remember { mutableStateOf(state.selectedBoneId ?: tracks.firstOrNull()?.boneId) }
    val selected = tracks.firstOrNull { it.boneId == selectedId }
    val frame = state.currentAnimFrame
    val accent = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.onSurface

    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dopesheet_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = FernScale.s512).verticalScroll(rememberScrollState())) {
                if (tracks.isEmpty()) {
                    Text(stringResource(R.string.dopesheet_empty))
                    return@Column
                }
                val scroll = rememberScrollState()
                Row {
                    Column {
                        Text("", modifier = Modifier.height(CELL.dp))
                        for (track in tracks) {
                            Text(
                                track.label,
                                fontWeight = if (track.boneId == selectedId) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.height(CELL.dp).padding(end = FernScale.s8).clickable { selectedId = track.boneId },
                            )
                        }
                    }
                    Column(modifier = Modifier.horizontalScroll(scroll)) {
                        Row {
                            for (f in 0 until state.animFrameCount) {
                                Box(modifier = Modifier.size(CELL.dp), contentAlignment = Alignment.Center) {
                                    Text("${f + 1}", fontWeight = if (f == frame) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        for (track in tracks) {
                            Row {
                                for (f in 0 until state.animFrameCount) {
                                    val mark = when {
                                        track.angles[f] == null -> ""
                                        track.keys[f] -> "●"
                                        else -> "–"
                                    }
                                    val cellLabel = stringResource(R.string.dopesheet_cell, track.label, f + 1)
                                    Box(
                                        modifier = Modifier
                                            .size(CELL.dp)
                                            .background(if (f == frame) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                            .semantics { contentDescription = cellLabel }
                                            .clickable {
                                                state.goToAnimFrame(f)
                                                state.selectedBoneId = track.boneId
                                                selectedId = track.boneId
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(mark) }
                                }
                            }
                        }
                    }
                }
                if (selected != null) {
                    Text(stringResource(R.string.dopesheet_curve, selected.label), modifier = Modifier.padding(top = FernScale.s16), style = MaterialTheme.typography.labelLarge)
                    val curveLabel = stringResource(R.string.dopesheet_curve, selected.label)
                    // The curve's points line up under the dopesheet's frame columns.
                    Box(modifier = Modifier.horizontalScroll(scroll)) {
                        Canvas(
                            modifier = Modifier
                                .width((CELL * state.animFrameCount).dp)
                                .height(CURVE_HEIGHT.dp)
                                .semantics { contentDescription = curveLabel }
                                .pointerInput(selected.boneId, state.animFrameCount) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val f = (down.position.x / size.width * state.animFrameCount).toInt().coerceIn(0, state.animFrameCount - 1)
                                        if (selected.angles[f] == null) return@awaitEachGesture
                                        down.consume()
                                        var first = true // one drag, one undo step
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            state.setBoneAngle(selected.boneId, f, angleAt(change.position.y, size.height.toFloat()), recordUndo = first)
                                            first = false
                                            change.consume()
                                        }
                                    }
                                },
                        ) {
                            val step = size.width / state.animFrameCount
                            // Zero and ±90° guides.
                            for (guide in listOf(0.0, PI / 2, -PI / 2)) {
                                val y = yOf(guide, size.height)
                                drawLine(lineColor.copy(alpha = if (guide == 0.0) 0.5f else 0.2f), Offset(0f, y), Offset(size.width, y))
                            }
                            val path = Path()
                            var started = false
                            for ((f, angle) in selected.angles.withIndex()) {
                                if (angle == null) continue
                                val point = Offset(step * (f + 0.5f), yOf(angle, size.height))
                                if (started) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                                started = true
                                drawCircle(if (f == frame) accent else lineColor, radius = if (selected.keys[f]) 6f else 3f, center = point)
                            }
                            drawPath(path, accent, style = DrawStyle(width = 2f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        dismissButton = {
            // Hold: this frame repeats the one before, for the selected bone.
            val previous = selected?.angles?.getOrNull(frame - 1)
            TextButton(
                onClick = { if (selected != null && previous != null) state.setBoneAngle(selected.boneId, frame, previous) },
                enabled = previous != null && selected?.angles?.getOrNull(frame) != null,
            ) { Text(stringResource(R.string.dopesheet_hold)) }
        },
    )
}

// Cell size of the grid, and the curve's height; on the §7 scale.
private const val CELL = 32
private const val CURVE_HEIGHT = 128

/** The curve shows angles from -180° (bottom) to 180° (top). */
private fun yOf(angle: Double, height: Float): Float = (height / 2 - angle / PI * height / 2).toFloat()

private fun angleAt(y: Float, height: Float): Double = ((height / 2 - y) / (height / 2) * PI).coerceIn(-PI, PI)
