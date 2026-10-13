package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernPanel

import com.andene.fern.FernScale

import androidx.annotation.StringRes
import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/** How new fills are shaded: solid, faded to transparent (top to bottom), or a two-colour gradient. */
enum class GradientMode(@StringRes val label: Int) { NONE(R.string.gradient_solid), FADE(R.string.gradient_fade), TWO_COLOURS(R.string.gradient_two) }

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
    // Resolved here: semantics blocks aren't composable contexts.
    val chooseEndLabel = stringResource(R.string.gradient_choose_end)
    val previewLabel = stringResource(R.string.gradient_preview)

    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gradient_title)) },
        text = {
            // Scrolls: a panel on an upright phone gets half the screen (design roadmap D42),
            // too little for both rows of directions and the preview.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    for (option in GradientMode.entries) {
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = { Text(stringResource(option.label)) },
                            modifier = Modifier.padding(end = FernScale.s4),
                        )
                    }
                }
                if (mode == GradientMode.TWO_COLOURS) {
                    Row(modifier = Modifier.padding(top = FernScale.s8), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.gradient_end), modifier = Modifier.padding(end = FernScale.s8))
                        Box(
                            modifier = Modifier
                                .size(FernScale.s32)
                                .background(endColor, CircleShape)
                                .border(FernScale.s1, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { pickingEndColor = true }
                                .semantics { contentDescription = chooseEndLabel },
                        )
                    }
                    // Two rows of four directions.
                    for (row in GRADIENT_DIRECTIONS.chunked(4)) {
                        Row(modifier = Modifier.padding(top = FernScale.s4)) {
                            for ((degrees, arrow) in row) {
                                val directionLabel = stringResource(R.string.gradient_direction, degrees)
                                FilterChip(
                                    selected = angle == degrees,
                                    onClick = { angle = degrees },
                                    label = { Text(arrow) },
                                    modifier = Modifier.padding(end = FernScale.s4).semantics { contentDescription = directionLabel },
                                )
                            }
                        }
                    }
                }
                // Live preview of what the next fill will look like.
                Box(
                    modifier = Modifier
                        .padding(top = FernScale.s8)
                        .fillMaxWidth()
                        .height(FernScale.s48)
                        .border(FernScale.s1, MaterialTheme.colorScheme.outline)
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
                        .semantics { contentDescription = previewLabel },
                )
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                onApply(mode, if (mode == GradientMode.TWO_COLOURS) FillGradient(endColor, angle) else null)
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
