package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * A full HSV + hex color picker, covering the whole color space rather than a fixed swatch
 * set. HSV sliders and the hex field are two views onto the same color: moving a slider
 * updates the hex text; typing a valid hex code updates the sliders. A 2D saturation/value
 * gradient box (the usual picker visual) is deliberately not implemented here - three
 * sliders give equivalent color-space coverage with far less touch-handling code, which is
 * the right tradeoff for this app's scope.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var hexText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        hexText = hexOf(hue, saturation, value)
    }

    fun currentColor() = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose color") },
        text = {
            Column {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = currentColor(),
                ) {}

                Text("Hue", modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        hexText = hexOf(hue, saturation, value)
                    },
                    valueRange = 0f..360f,
                )

                Text("Saturation")
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        hexText = hexOf(hue, saturation, value)
                    },
                    valueRange = 0f..1f,
                )

                Text("Brightness")
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        hexText = hexOf(hue, saturation, value)
                    },
                    valueRange = 0f..1f,
                )

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            parseHex(text)?.let { argb ->
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(currentColor()) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun hexOf(hue: Float, saturation: Float, value: Float): String {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    return String.format("%06X", argb and 0xFFFFFF)
}

/** Parses a 6-digit hex color (with or without a leading '#'), or null if invalid. */
private fun parseHex(text: String): Int? {
    val cleaned = text.removePrefix("#")
    if (cleaned.length != 6) return null
    return try {
        android.graphics.Color.parseColor("#$cleaned")
    } catch (e: IllegalArgumentException) {
        null
    }
}
