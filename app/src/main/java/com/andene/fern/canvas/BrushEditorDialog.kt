package com.andene.fern.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Brush editor for the stamp pen: pick a tip and tune spacing, scatter, size and angle jitter
 * and direction-following, with a live preview stroke. Edits stay local until Apply, so Cancel
 * (or dismissing) leaves the brush as it was; Reset returns to the default brush.
 */
@Composable
fun BrushEditorDialog(
    initialBrush: StampBrush,
    color: Color,
    onApply: (StampBrush) -> Unit,
    onDismiss: () -> Unit,
) {
    var brush by remember { mutableStateOf(initialBrush) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stamp brush") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BrushPreview(brush, color)
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    for (tip in StampTip.entries) {
                        FilterChip(
                            selected = brush.tip == tip,
                            onClick = { brush = brush.copy(tip = tip) },
                            label = { Text(tip.label) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
                BrushSlider("Spacing", brush.spacing, StampBrush.MIN_SPACING..StampBrush.MAX_SPACING) { brush = brush.copy(spacing = it) }
                BrushSlider("Scatter", brush.scatter, 0f..StampBrush.MAX_SCATTER) { brush = brush.copy(scatter = it) }
                BrushSlider("Size jitter", brush.sizeJitter, 0f..1f) { brush = brush.copy(sizeJitter = it) }
                BrushSlider("Angle jitter", brush.angleJitter, 0f..1f) { brush = brush.copy(angleJitter = it) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Follow stroke direction", modifier = Modifier.weight(1f))
                    Switch(checked = brush.followDirection, onCheckedChange = { brush = brush.copy(followDirection = it) })
                }
                TextButton(onClick = { brush = StampBrush() }) { Text("Reset to default") }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(brush) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BrushSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

/** A sample S-curve drawn with [brush] in [color], redrawn on every change. */
@Composable
private fun BrushPreview(brush: StampBrush, color: Color) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .border(1.dp, outline)
            .semantics { contentDescription = "Brush preview" },
    ) {
        val margin = 24.dp.toPx()
        val width = 16.dp.toPx()
        val path = (0..64).map { k ->
            val t = k / 64f
            Offset(margin + t * (size.width - 2 * margin), size.height / 2f + sin(t * 2f * PI.toFloat()) * (size.height / 2f - margin))
        }
        for (stamp in stampPlacements(path, width, brush)) drawStamp(brush.tip, stamp, color)
    }
}
