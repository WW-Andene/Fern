package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import com.andene.fern.FernTextField
import com.andene.fern.FernPanel

import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.ui.platform.LocalContext
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
    /** A non-blocking panel that applies each change as it's made, instead of a dialog with Select (design roadmap D20). */
    live: Boolean = false,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var hexText by remember { mutableStateOf("") }
    var spotName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        hexText = hexOf(hue, saturation, value)
    }

    fun currentColor() = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    // Kept as typed while they still describe the colour (within a percent), so typing isn't
    // overwritten by rounding; recomputed when another control changes the colour.
    var cmykTexts by remember { mutableStateOf(List(4) { "" }) }
    LaunchedEffect(hue, saturation, value) {
        val now = cmykOf(currentColor())
        val typed = cmykTexts.map { it.trim().replace(',', '.').toFloatOrNull() }
        if (typed.indices.any { i -> typed[i]?.let { kotlin.math.abs(it - now[i] * 100f) > 1f } ?: true }) {
            cmykTexts = now.map { "%.0f".format(it * 100f) }
        }
    }

    val pickerTitle: @Composable () -> Unit = { Text(stringResource(R.string.color_title)) }
    val pickerText: @Composable () -> Unit = {
            // Scrolls: with the harmony rows the picker is taller than a landscape phone screen.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    modifier = Modifier.size(FernScale.s48),
                    shape = CircleShape,
                    color = currentColor(),
                ) {}

                Text(stringResource(R.string.color_hue), modifier = Modifier.padding(top = FernScale.s8))
                FernSlider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        hexText = hexOf(hue, saturation, value)
                    },
                    valueRange = 0f..360f,
                )

                Text(stringResource(R.string.color_saturation))
                FernSlider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        hexText = hexOf(hue, saturation, value)
                    },
                    valueRange = 0f..1f,
                )

                Text(stringResource(R.string.color_brightness))
                FernSlider(
                    value = value,
                    onValueChange = {
                        value = it
                        hexText = hexOf(hue, saturation, value)
                    },
                    valueRange = 0f..1f,
                )

                // Colour harmonies: hues related to the current one by the classic colour-wheel
                // rules. Tapping a swatch makes it the current colour.
                Text(stringResource(R.string.color_harmonies), modifier = Modifier.padding(top = FernScale.s8))
                for ((label, offsets) in HARMONIES) {
                    Row(
                        modifier = Modifier.padding(vertical = FernScale.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), modifier = Modifier.width(FernScale.s128))
                        for (offset in offsets) {
                            val swatchHue = (hue + offset) % 360f
                            Surface(
                                modifier = Modifier
                                    .padding(end = FernScale.s8)
                                    .minimumInteractiveComponentSize().size(FernScale.s24)
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

                Row(modifier = Modifier.padding(top = FernScale.s8)) {
                    FernTextField(
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
                        label = { Text(stringResource(R.string.color_hex)) },
                        singleLine = true,
                    )
                }
                // CMYK (roadmap item 102) in percent: through the loaded ICC profile when there is
                // one (Settings), else plain device CMYK - a way to enter print values.
                Text(
                    ColorManagement.cmykProfile?.let { stringResource(R.string.color_cmyk_profile, it.description.ifEmpty { "ICC" }) } ?: stringResource(R.string.color_cmyk),
                    modifier = Modifier.padding(top = FernScale.s8),
                )
                Row {
                    for ((channel, label) in listOf("C", "M", "Y", "K").withIndex()) {
                        FernTextField(
                            value = cmykTexts[channel],
                            onValueChange = { text ->
                                cmykTexts = cmykTexts.toMutableList().also { it[channel] = text }
                                val parts = cmykTexts.map { it.trim().replace(',', '.').toFloatOrNull() }
                                if (parts.all { it != null }) {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(colorOfCmyk(parts.map { it!! / 100f }).toArgb(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                    hexText = hexOf(hue, saturation, value)
                                }
                            },
                            label = { Text(label) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.width(FernScale.s62).padding(end = FernScale.s4),
                        )
                    }
                }
                // Spot colours (roadmap item 102): named inks, kept as their own separations in print PDFs.
                val context = LocalContext.current
                Text(stringResource(R.string.color_spots), modifier = Modifier.padding(top = FernScale.s8))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for (spot in ColorManagement.spotColors) {
                        InputChip(
                            selected = spot.color == currentColor(),
                            onClick = {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(spot.color.toArgb(), hsv)
                                hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                                hexText = hexOf(hue, saturation, value)
                            },
                            label = { Text(spot.name) },
                            leadingIcon = { Box(Modifier.size(FernScale.s16).background(spot.color, CircleShape)) },
                            trailingIcon = {
                                Icon(
                                    FernIcons.Close,
                                    contentDescription = stringResource(R.string.color_spot_delete, spot.name),
                                    modifier = Modifier.minimumInteractiveComponentSize().size(FernScale.s16).clickable { ColorManagement.deleteSpot(context, spot.name) },
                                )
                            },
                            modifier = Modifier.padding(end = FernScale.s4),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FernTextField(
                        value = spotName,
                        onValueChange = { spotName = it },
                        label = { Text(stringResource(R.string.color_spot_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FernTextButton(onClick = {
                        if (spotName.isNotBlank()) {
                            val color = currentColor()
                            ColorManagement.saveSpot(context, SpotColor(spotName.trim(), color, cmykOf(color)))
                            spotName = ""
                        }
                    }) { Text(stringResource(R.string.color_spot_save)) }
                }
            }
        }
    val pickerConfirm: @Composable () -> Unit = {
            FernTextButton(onClick = { onColorSelected(currentColor()) }) {
                Text(stringResource(R.string.action_select))
            }
        }
    val pickerDismiss: @Composable () -> Unit = {
            Row {
                if (onImportPalette != null) {
                    FernTextButton(onClick = onImportPalette) { Text(stringResource(R.string.color_import_palette)) }
                }
                if (onSaveColor != null) {
                    FernTextButton(onClick = { onSaveColor(currentColor()) }) { Text(stringResource(R.string.color_save_palette)) }
                }
                FernTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    if (live) {
        // Inline and live (design roadmap D20): each change applies at once, the canvas stays usable.
        LaunchedEffect(hue, saturation, value) { if (hexText.isNotEmpty()) onColorSelected(currentColor()) }
        FernPanel(onDismissRequest = onDismiss, title = pickerTitle, text = pickerText, confirmButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } }, dismissButton = {
            Row {
                if (onImportPalette != null) FernTextButton(onClick = onImportPalette) { Text(stringResource(R.string.color_import_palette)) }
                if (onSaveColor != null) FernTextButton(onClick = { onSaveColor(currentColor()) }) { Text(stringResource(R.string.color_save_palette)) }
            }
        })
    } else {
        FernPanel(onDismissRequest = onDismiss, title = pickerTitle, text = pickerText, confirmButton = pickerConfirm, dismissButton = pickerDismiss)
    }
}

/** Hue offsets in degrees from the current colour, per harmony rule. */
private val HARMONIES = listOf(
    R.string.harmony_complementary to listOf(180f),
    R.string.harmony_analogous to listOf(330f, 30f),
    R.string.harmony_triadic to listOf(120f, 240f),
    R.string.harmony_split to listOf(150f, 210f),
    R.string.harmony_tetradic to listOf(90f, 180f, 270f),
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

/**
 * [color]'s CMYK ink amounts (0..1): through the loaded CMYK ICC profile (roadmap item 102) when
 * there is one, else plain uncalibrated device CMYK.
 */
internal fun cmykOf(color: Color): List<Float> {
    ColorManagement.cmykProfile?.let { return it.toCmyk(floatArrayOf(color.red, color.green, color.blue)).toList() }
    val k = 1f - maxOf(color.red, color.green, color.blue)
    if (k >= 1f) return listOf(0f, 0f, 0f, 1f)
    return listOf((1f - color.red - k) / (1f - k), (1f - color.green - k) / (1f - k), (1f - color.blue - k) / (1f - k), k)
}

/** The RGB colour of [cmyk] (0..1 each, clamped): through the loaded CMYK profile when there is one, else device CMYK. */
internal fun colorOfCmyk(cmyk: List<Float>): Color {
    val (c, m, y, k) = cmyk.map { it.coerceIn(0f, 1f) }
    ColorManagement.cmykProfile?.let { profile -> return profile.toRgb(floatArrayOf(c, m, y, k)).let { Color(it[0], it[1], it[2]) } }
    return Color((1 - c) * (1 - k), (1 - m) * (1 - k), (1 - y) * (1 - k))
}
