package com.andene.fern.canvas

import com.andene.fern.FernScale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.andene.fern.R

/**
 * Graph editor for a custom timing curve (roadmap item 81): time runs left to right, progress
 * bottom to top; drag either handle to shape the curve. Progress may go a quarter beyond 0..1
 * for anticipation and overshoot, so the graph leaves room above and below.
 */
@Composable
fun EasingCurveDialog(initial: CubicEasing, onApply: (CubicEasing) -> Unit, onDismiss: () -> Unit) {
    var curve by remember { mutableStateOf(initial) }
    // Which handle a drag moves: 1 or 2, chosen by the nearer one when it starts.
    var dragging by remember { mutableIntStateOf(0) }
    val lineColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.easing_curve_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(description) },
        text = {
            Column {
                Canvas(
                    modifier = Modifier
                        .size(FernScale.s192)
                        .semantics { contentDescription = description }
                        .pointerInput(Unit) {
                            fun toCurve(p: Offset): Pair<Double, Double> {
                                val (x, y) = graphToUnit(p, size.width.toFloat(), size.height.toFloat())
                                return x.coerceIn(0.0, 1.0) to y.coerceIn(-OVERSHOOT, 1 + OVERSHOOT)
                            }
                            detectDragGestures(
                                onDragStart = { start ->
                                    val (x, y) = toCurve(start)
                                    val d1 = (x - curve.x1) * (x - curve.x1) + (y - curve.y1) * (y - curve.y1)
                                    val d2 = (x - curve.x2) * (x - curve.x2) + (y - curve.y2) * (y - curve.y2)
                                    dragging = if (d1 <= d2) 1 else 2
                                },
                                onDragEnd = { dragging = 0 },
                            ) { change, _ ->
                                val (x, y) = toCurve(change.position)
                                curve = if (dragging == 1) curve.copy(x1 = x, y1 = y) else curve.copy(x2 = x, y2 = y)
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    fun point(x: Double, y: Double) = unitToGraph(x, y, w, h)
                    drawLine(lineColor.copy(alpha = 0.3f), point(0.0, 0.0), point(1.0, 0.0))
                    drawLine(lineColor.copy(alpha = 0.3f), point(0.0, 1.0), point(1.0, 1.0))
                    drawLine(lineColor.copy(alpha = 0.3f), point(0.0, 0.0), point(0.0, 1.0))
                    drawLine(lineColor.copy(alpha = 0.3f), point(1.0, 0.0), point(1.0, 1.0))
                    val path = Path()
                    for (k in 0..CURVE_SAMPLES) {
                        val s = k.toDouble() / CURVE_SAMPLES
                        val p = point(CubicEasing.bezier(s, curve.x1, curve.x2), CubicEasing.bezier(s, curve.y1, curve.y2))
                        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(path, lineColor, style = DrawStyle(width = 3f))
                    for ((anchor, handle) in listOf(point(0.0, 0.0) to point(curve.x1, curve.y1), point(1.0, 1.0) to point(curve.x2, curve.y2))) {
                        drawLine(accent, anchor, handle, strokeWidth = 2f)
                        drawCircle(accent, radius = 12f, center = handle)
                    }
                }
                Text(
                    "%.2f, %.2f, %.2f, %.2f".format(curve.x1, curve.y1, curve.x2, curve.y2),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = FernScale.s8),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(curve) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// Room for progress beyond 0..1 (anticipation, overshoot), as a fraction of the 0..1 range.
private const val OVERSHOOT = 0.25
private const val CURVE_SAMPLES = 64

/** Graph pixel position of curve point ([x], [y]) in a [w] x [h] graph with [OVERSHOOT] margins. */
internal fun unitToGraph(x: Double, y: Double, w: Float, h: Float): Offset {
    val range = 1 + 2 * OVERSHOOT
    return Offset((x * w).toFloat(), (h * (1 - (y + OVERSHOOT) / range)).toFloat())
}

/** The inverse of [unitToGraph]. */
internal fun graphToUnit(p: Offset, w: Float, h: Float): Pair<Double, Double> {
    val range = 1 + 2 * OVERSHOOT
    return p.x.toDouble() / w to (1 - p.y.toDouble() / h) * range - OVERSHOOT
}
