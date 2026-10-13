package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernTextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
internal val HARMONY_TURNS = listOf(180f, 30f, 330f, 120f, 240f)

/** [color] turned [degrees] round the colour wheel, same saturation and brightness. */
fun turnHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * The colour row (design roadmap D20, roadmap item 351), kept short so it fits one row of the
 * properties bar on a phone: the current colour (tap: the full picker, live and non-blocking),
 * the last few colours used, the first colours of the active palette, and a button opening the
 * palette panel, where every palette, its colours and the current colour's harmonies are.
 * While the current colour is in the palette, changing it everywhere is a visible button.
 */
@Composable
fun ColorStrip(state: CanvasState, showPalette: Boolean, onOpenPicker: () -> Unit, onOpenPalettes: () -> Unit, onChangeEverywhere: (Color) -> Unit) {
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
            FernTextButton(onClick = { onChangeEverywhere(current) }) { Text(stringResource(R.string.color_change_everywhere)) }
        }
        val recentLabel = stringResource(R.string.color_recent)
        for (color in state.recentColors.filter { it != current }.take(STRIP_RECENT)) Swatch(color, CircleShape, recentLabel) { pick(color) }
        if (showPalette) {
            val savedLabel = stringResource(R.string.color_saved)
            for (color in state.savedColors.take(STRIP_PALETTE)) Swatch(color, RoundedCornerShape(FernScale.s6), savedLabel) { pick(color) } // §7.3: 0.24 × 24 → 6
        }
        IconButton(onClick = onOpenPalettes) { Icon(FernIcons.Palette, contentDescription = stringResource(R.string.palette_panel_title)) }
    }
}

// How many recent colours and palette colours the row shows: with the current colour and the
// palette button, a 360 dp phone's row (§7: 4 and 8 on the scale).
private const val STRIP_RECENT = 4
private const val STRIP_PALETTE = 8

@Composable
internal fun Swatch(color: Color, shape: androidx.compose.ui.graphics.Shape, label: String, onPick: () -> Unit) {
    Surface(
        modifier = Modifier.minimumInteractiveComponentSize().size(FernSize.swatch).semantics { contentDescription = label }.clickable(onClick = onPick),
        shape = shape,
        color = color,
    ) {}
}

/** Tools that draw with the current colour; picking a colour with another tool switches to the pen. */
internal val COLOUR_TOOLS = setOf(Tool.PEN, Tool.SHAPE, Tool.BEZIER, Tool.FILL, Tool.TEXT)
