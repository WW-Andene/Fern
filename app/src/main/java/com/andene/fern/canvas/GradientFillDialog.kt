package com.andene.fern.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** How new fills are shaded: solid, faded to transparent (top to bottom), or a two-colour gradient. */
enum class GradientMode(val label: String) { NONE("Solid"), FADE("Fade out"), TWO_COLOURS("Two colours") }

/**
 * Gradient options for the fill tool. A two-colour gradient runs from the active colour to an
 * end colour picked here, in one of eight directions, previewed live. Apply sets the mode for
 * the next fills; Cancel leaves it unchanged.
 */
@Composable
fun GradientFillDialog(
    startColor: Color,
    initialMode: GradientMode,
    initialGradient: FillGradient?,
    onApply: (GradientMode, FillGradient?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var endColor by remember { mutableStateOf(initialGradient?.endColor ?: Color.White) }
    var angle by remember { mutableIntStateOf(initialGradient?.angleDegrees ?: 90) }
    var pickingEndColor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gradient fill") },
        text = {
            Column {
                Row {
                    for (option in GradientMode.entries) {
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = { Text(option.label) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
                if (mode == GradientMode.TWO_COLOURS) {
                    Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("End colour", modifier = Modifier.padding(end = 8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(endColor, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { pickingEndColor = true }
                                .semantics { contentDescription = "Choose the end colour" },
                        )
                    }
                    // Two rows of four directions.
                    for (row in GRADIENT_DIRECTIONS.chunked(4)) {
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            for ((degrees, arrow) in row) {
                                FilterChip(
                                    selected = angle == degrees,
                                    onClick = { angle = degrees },
                                    label = { Text(arrow) },
                                    modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = "Direction $degrees degrees" },
                                )
                            }
                        }
                    }
                }
                // Live preview of what the next fill will look like.
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .drawBehind {
                            val corners = listOf(Offset.Zero, Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
                            when (mode) {
                                GradientMode.NONE -> drawRect(startColor)
                                GradientMode.FADE -> drawRect(Brush.verticalGradient(listOf(startColor, startColor.copy(alpha = 0f))))
                                GradientMode.TWO_COLOURS -> {
                                    val (start, end) = gradientEndpoints(corners, angle)
                                    drawRect(Brush.linearGradient(listOf(startColor, endColor), start = start, end = end))
                                }
                            }
                        }
                        .semantics { contentDescription = "Gradient preview" },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(mode, if (mode == GradientMode.TWO_COLOURS) FillGradient(endColor, angle) else null)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingEndColor) {
        ColorPickerDialog(
            initialColor = endColor,
            onDismiss = { pickingEndColor = false },
            onColorSelected = {
                endColor = it
                pickingEndColor = false
            },
        )
    }
}
