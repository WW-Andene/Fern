package com.andene.fern.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
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
    /** When set, a "Save to palette" button stores the current colour without closing. */
    onSaveColor: ((Color) -> Unit)? = null,
    /** When set, an "Import palette" button imports a .gpl or .ase palette file into the saved colours. */
    onImportPalette: (() -> Unit)? = null,
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
            // Scrolls: with the harmony rows the picker is taller than a landscape phone screen.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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

                // Colour harmonies: hues related to the current one by the classic colour-wheel
                // rules. Tapping a swatch makes it the current colour.
                Text("Harmonies", modifier = Modifier.padding(top = 8.dp))
                for ((label, offsets) in HARMONIES) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.width(128.dp))
                        for (offset in offsets) {
                            val swatchHue = (hue + offset) % 360f
                            Surface(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(24.dp)
                                    .clickable {
                                        hue = swatchHue
                                        hexText = hexOf(hue, saturation, value)
                                    },
                                shape = CircleShape,
                                color = Color(android.graphics.Color.HSVToColor(floatArrayOf(swatchHue, saturation, value))),
                            ) {}
                        }
                    }
                }

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
            Row {
                if (onImportPalette != null) {
                    TextButton(onClick = onImportPalette) { Text("Import palette") }
                }
                if (onSaveColor != null) {
                    TextButton(onClick = { onSaveColor(currentColor()) }) { Text("Save to palette") }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

/** Hue offsets in degrees from the current colour, per harmony rule. */
private val HARMONIES = listOf(
    "Complementary" to listOf(180f),
    "Analogous" to listOf(330f, 30f),
    "Triadic" to listOf(120f, 240f),
    "Split compl." to listOf(150f, 210f),
    "Tetradic" to listOf(90f, 180f, 270f),
)

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

/**
 * Parses a GIMP palette (.gpl, also used by Krita, Inkscape and Aseprite): a "GIMP Palette"
 * header, optional "Name:"/"Columns:" lines and "#" comments, then one "R G B [name]" line per
 * colour. Lines that don't parse are skipped; returns an empty list if the header is missing.
 */
fun parseGimpPalette(text: String): List<Color> {
    val lines = text.lineSequence().map { it.trim() }.toList()
    if (lines.firstOrNull() != "GIMP Palette") return emptyList()
    return lines.drop(1).mapNotNull { line ->
        if (line.isEmpty() || line.startsWith("#") || line.contains(':')) return@mapNotNull null
        val parts = line.split(Regex("\\s+"))
        val rgb = parts.take(3).map { it.toIntOrNull() }
        if (rgb.size < 3 || rgb.any { it == null || it !in 0..255 }) null
        else Color(rgb[0]!!, rgb[1]!!, rgb[2]!!)
    }
}
