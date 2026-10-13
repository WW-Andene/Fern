package com.andene.fern.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.andene.fern.FernScale
import com.andene.fern.FernSize
import com.andene.fern.R

/** Hue turns giving the current colour's harmonies: complementary, analogous either side, triadic. */
private val HARMONY_TURNS = listOf(180f, 30f, 330f, 120f, 240f)

/** [color] turned [degrees] round the colour wheel, same saturation and brightness. */
fun turnHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The one colour strip (design roadmap D20): the current colour (tap: the full picker, live and
 * non-blocking), the saved colours, recent ones, the current colour's harmonies and the palette,
 * in one row. While the current colour is a saved one, changing it everywhere and removing it
 * are visible buttons, not a hidden long-press.
 */
@Composable
fun ColorStrip(state: CanvasState, palette: List<Color>, showPalette: Boolean, onOpenPicker: () -> Unit, onChangeEverywhere: (Color) -> Unit) {
    val current = state.activeColor
    fun pick(color: Color) {
        state.activeColor = color
        if (state.activeTool !in COLOUR_TOOLS) state.activeTool = Tool.PEN
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val currentLabel = stringResource(R.string.color_current)
        Surface(
            modifier = Modifier.minimumInteractiveComponentSize().size(FernScale.s32).semantics { contentDescription = currentLabel }.clickable(onClick = onOpenPicker),
            shape = CircleShape,
            color = current,
            border = BorderStroke(FernScale.s2, MaterialTheme.colorScheme.primary),
        ) {}
        if (current in state.savedColors) {
            TextButton(onClick = { onChangeEverywhere(current) }) { Text(stringResource(R.string.color_change_everywhere)) }
            TextButton(onClick = { state.removeSavedColor(current) }) { Text(stringResource(R.string.color_remove_saved)) }
        }
        for (color in state.savedColors) Swatch(color, RoundedCornerShape(FernScale.s6), stringResource(R.string.color_saved)) { pick(color) } // §7.3: 0.24 × 24 → 6
        for (color in state.recentColors.filter { it !in state.savedColors && it != current }) Swatch(color, CircleShape, stringResource(R.string.color_recent)) { pick(color) }
        val harmonyLabel = stringResource(R.string.color_harmonies)
        for (turn in HARMONY_TURNS) Swatch(turnHue(current, turn), CircleShape, harmonyLabel) { pick(turnHue(current, turn)) }
        if (showPalette) for (color in palette) Swatch(color, CircleShape, stringResource(R.string.color_palette)) { pick(color) }
    }
}

@Composable
private fun Swatch(color: Color, shape: androidx.compose.ui.graphics.Shape, label: String, onPick: () -> Unit) {
    Surface(
        modifier = Modifier.minimumInteractiveComponentSize().size(FernSize.swatch).semantics { contentDescription = label }.clickable(onClick = onPick),
        shape = shape,
        color = color,
    ) {}
}

/** Tools that draw with the current colour; picking a colour with another tool switches to the pen. */
private val COLOUR_TOOLS = setOf(Tool.PEN, Tool.SHAPE, Tool.BEZIER, Tool.FILL, Tool.TEXT)
