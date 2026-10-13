package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlin.math.roundToInt

/**
 * Turning raster pixels (roadmap item 145): the angle, and RotSprite - which keeps pixel art's
 * lines clean - or plain nearest-neighbour turning. Applies to the selection, or to the layer's
 * paint without one (undoable).
 */
@Composable
fun RotatePixelsDialog(state: CanvasState, onDismiss: () -> Unit) {
    var degrees by remember { mutableFloatStateOf(45f) }
    var rotSprite by remember { mutableStateOf(true) }
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rotate_pixels_title)) },
        text = {
            Column {
                Text(stringResource(R.string.rotate_pixels_angle, degrees.roundToInt()))
                FernSlider(value = degrees, onValueChange = { degrees = it.roundToInt().toFloat() }, valueRange = -180f..180f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rotSprite, onCheckedChange = { rotSprite = it })
                    Text(stringResource(R.string.rotate_pixels_rotsprite))
                }
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                state.rotateRasterPixels(degrees.toDouble(), rotSprite)
                onDismiss()
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
