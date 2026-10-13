package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import com.andene.fern.FernScale

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andene.fern.R

/**
 * A frame's size (roadmap item 144), in the drawing scale's unit when there is one (roadmap
 * item 143): its top-left corner stays, and the objects on it follow their constraints.
 */
@Composable
fun FrameResizeDialog(frame: Frame, drawingScale: DrawingScale?, onApply: (width: Double, height: Double) -> Unit, onDismiss: () -> Unit) {
    val k = drawingScale.factor
    var width by remember { mutableStateOf(formatMeasure((frame.max.x - frame.min.x) * k)) }
    var height by remember { mutableStateOf(formatMeasure((frame.max.y - frame.min.y) * k)) }
    fun parse(text: String) = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.frame_resize_title, frame.name)) },
        text = {
            Column {
                Row {
                    FernTextField(width, { width = it }, label = { Text("${stringResource(R.string.transform_width)} (${drawingScale.symbol})") }, singleLine = true, isError = parse(width) == null, keyboardOptions = numeric, modifier = Modifier.width(FernScale.s128).padding(end = FernScale.s8))
                    FernTextField(height, { height = it }, label = { Text("${stringResource(R.string.transform_height)} (${drawingScale.symbol})") }, singleLine = true, isError = parse(height) == null, keyboardOptions = numeric, modifier = Modifier.width(FernScale.s128))
                }
                Text(stringResource(R.string.frame_resize_hint))
            }
        },
        confirmButton = {
            FernTextButton(onClick = {
                val w = parse(width) ?: return@FernTextButton
                val h = parse(height) ?: return@FernTextButton
                onApply(w / k, h / k)
            }, enabled = parse(width) != null && parse(height) != null) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
