package com.andene.fern.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.andene.fern.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Which adjustment the dialog edits. */
private enum class AdjustmentKind(val label: Int) {
    LEVELS(R.string.adjust_levels),
    CURVES(R.string.adjust_curves),
    HUE(R.string.adjust_hue),
}

/**
 * Adjustments for the active raster layer (roadmap item 92) - levels, curves or
 * hue/saturation/lightness - previewed live on the layer (within its selection, if any). Apply
 * keeps the result as one undo step; Cancel or dismissing puts the layer back.
 */
@Composable
fun AdjustmentsDialog(state: CanvasState, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(AdjustmentKind.LEVELS) }
    var levels by remember { mutableStateOf(Levels()) }
    var curves by remember { mutableStateOf(Curves()) }
    var hue by remember { mutableStateOf(HueSaturation()) }
    val current: Adjustment = when (kind) {
        AdjustmentKind.LEVELS -> levels
        AdjustmentKind.CURVES -> curves
        AdjustmentKind.HUE -> hue
    }
    // Previewed after a short pause, so dragging a slider doesn't recompute every pixel per step.
    LaunchedEffect(current) {
        delay(PREVIEW_DELAY_MILLIS)
        state.previewAdjustment(current)
    }
    AlertDialog(
        onDismissRequest = { state.cancelAdjustment(); onDismiss() },
        title = { Text(stringResource(R.string.adjust_title)) },
        text = {
            Column {
                Row {
                    for (k in AdjustmentKind.entries) {
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(k.label)) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                when (kind) {
                    AdjustmentKind.LEVELS -> {
                        LabeledSlider(stringResource(R.string.adjust_in_black), levels.inBlack, 0f..1f) { levels = levels.copy(inBlack = it) }
                        LabeledSlider(stringResource(R.string.adjust_in_white), levels.inWhite, 0f..1f) { levels = levels.copy(inWhite = it) }
                        LabeledSlider(stringResource(R.string.adjust_gamma), levels.gamma, 0.2f..5f) { levels = levels.copy(gamma = it) }
                        LabeledSlider(stringResource(R.string.adjust_out_black), levels.outBlack, 0f..1f) { levels = levels.copy(outBlack = it) }
                        LabeledSlider(stringResource(R.string.adjust_out_white), levels.outWhite, 0f..1f) { levels = levels.copy(outWhite = it) }
                    }
                    AdjustmentKind.CURVES -> CurveEditor(curves) { curves = it }
                    AdjustmentKind.HUE -> {
                        LabeledSlider(stringResource(R.string.adjust_hue_shift), hue.hueDegrees, -180f..180f) { hue = hue.copy(hueDegrees = it) }
                        LabeledSlider(stringResource(R.string.adjust_saturation), hue.saturation, -1f..1f) { hue = hue.copy(saturation = it) }
                        LabeledSlider(stringResource(R.string.adjust_lightness), hue.lightness, -1f..1f) { hue = hue.copy(lightness = it) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.previewAdjustment(current)
                state.commitAdjustment()
                onDismiss()
            }) { Text(stringResource(R.string.adjust_apply)) }
        },
        dismissButton = { TextButton(onClick = { state.cancelAdjustment(); onDismiss() }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

/**
 * A curves graph: input left to right, output bottom to top. Tap to add a point, drag the
 * nearest point to move it; a point dragged off the graph's sides is removed.
 */
@Composable
private fun CurveEditor(curves: Curves, onChange: (Curves) -> Unit) {
    val line = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    var dragging by remember { mutableIntStateOf(-1) }
    val description = stringResource(R.string.adjust_curves)
    Canvas(
        modifier = Modifier
            .size(192.dp)
            .semantics { contentDescription = description }
            .pointerInput(curves) {
                detectTapGestures { p ->
                    val point = (p.x / size.width).coerceIn(0f, 1f) to (1 - p.y / size.height).coerceIn(0f, 1f)
                    onChange(Curves((curves.points + point).sortedBy { it.first }))
                }
            }
            .pointerInput(curves) {
                detectDragGestures(
                    onDragStart = { p ->
                        val x = p.x / size.width
                        val y = 1 - p.y / size.height
                        dragging = curves.points.indices.minByOrNull { i ->
                            val (px, py) = curves.points[i]
                            (px - x) * (px - x) + (py - y) * (py - y)
                        } ?: -1
                    },
                    onDragEnd = { dragging = -1 },
                ) { change, _ ->
                    val i = dragging
                    if (i !in curves.points.indices) return@detectDragGestures
                    val x = change.position.x / size.width
                    val y = (1 - change.position.y / size.height).coerceIn(0f, 1f)
                    val points = curves.points.toMutableList()
                    if (x < -0.1f || x > 1.1f) {
                        points.removeAt(i)
                        dragging = -1
                    } else {
                        points[i] = x.coerceIn(0f, 1f) to y
                    }
                    onChange(Curves(points.sortedBy { it.first }))
                }
            },
    ) {
        val w = size.width
        val h = size.height
        drawLine(line.copy(alpha = 0.3f), Offset(0f, h), Offset(w, 0f))
        val path = Path()
        for (k in 0..64) {
            val x = k / 64f
            val p = Offset(x * w, (1 - curves.map(x)) * h)
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, line, style = DrawStyle(width = 3f))
        for ((x, y) in curves.points) drawCircle(accent, radius = 8f, center = Offset(x * w, (1 - y) * h))
    }
    Text(
        curves.points.joinToString { (x, y) -> "${(x * 255).roundToInt()}→${(y * 255).roundToInt()}" },
        style = MaterialTheme.typography.bodySmall,
    )
}

private const val PREVIEW_DELAY_MILLIS = 120L
