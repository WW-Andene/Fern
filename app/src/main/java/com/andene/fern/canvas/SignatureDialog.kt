package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernScale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * The signature pad (roadmap item 129): sign with a finger or pen, then place the signature on
 * the canvas, where it's an ordinary selected group of strokes to move, resize or recolour. The
 * last signature saved is already on the pad, so signing again is one tap on Place; Clear
 * starts over. Placing saves what's on the pad as the signature.
 */
@Composable
fun SignatureDialog(saved: List<Stroke>, onPlace: (List<Stroke>) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<Stroke>().apply { addAll(saved) } }
    // Points are added to a stroke in place; this makes the pad redraw for each one.
    val revision = remember { mutableIntStateOf(0) }

    FernPanel(
        // Not scrolled: a scroll would take the drags meant for the drawing area.
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.signature_title)) },
        text = {
            Column {
                Text(stringResource(R.string.signature_hint))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FernScale.s192)
                        .background(Color.White)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val stroke = Stroke(SIGNATURE_INK, SIGNATURE_WIDTH_PX, PenType.MARKER)
                                stroke.addPoint(WorldPoint(down.position.x.toDouble(), down.position.y.toDouble()), down.pressure)
                                strokes.add(stroke)
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    stroke.addPoint(WorldPoint(change.position.x.toDouble(), change.position.y.toDouble()), change.pressure)
                                    revision.intValue++
                                    change.consume()
                                }
                            }
                        },
                ) {
                    revision.intValue // read so the pad redraws as a stroke grows
                    for (stroke in strokes) {
                        val points = stroke.points
                        if (points.size == 1) {
                            drawCircle(stroke.color, radius = SIGNATURE_WIDTH_PX.toFloat() / 2, center = Offset(points[0].x.toFloat(), points[0].y.toFloat()))
                            continue
                        }
                        val path = Path()
                        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat()) }
                        drawPath(path, stroke.color, style = DrawStyle(width = SIGNATURE_WIDTH_PX.toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        },
        confirmButton = {
            FernTextButton(onClick = { onPlace(strokes.toList()) }, enabled = strokes.isNotEmpty()) {
                Text(stringResource(R.string.signature_place))
            }
        },
        dismissButton = {
            Row {
                FernTextButton(onClick = { strokes.clear() }) { Text(stringResource(R.string.signature_clear)) }
                FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private val SIGNATURE_INK = Color(0xFF1A2340) // blue-black, the usual signing ink
private const val SIGNATURE_WIDTH_PX = 3.0
