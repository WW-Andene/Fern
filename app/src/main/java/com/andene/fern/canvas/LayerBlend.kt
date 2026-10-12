package com.andene.fern.canvas

import androidx.compose.ui.graphics.BlendMode
import com.andene.fern.R
import kotlin.math.abs

/**
 * How a layer composites onto everything beneath it (roadmap item 93): Photoshop's full set of
 * layer blend modes, in its menu order. Those Android's renderer has are drawn on the GPU
 * ([gpu]); the rest ([gpu] null) are composited per pixel by [blendPixel] in a CPU pass.
 */
enum class LayerBlend(val label: Int, val gpu: BlendMode?) {
    NORMAL(R.string.blend_normal, BlendMode.SrcOver),
    DISSOLVE(R.string.blend_dissolve, null),
    DARKEN(R.string.blend_darken, BlendMode.Darken),
    MULTIPLY(R.string.blend_multiply, BlendMode.Multiply),
    COLOR_BURN(R.string.blend_color_burn, BlendMode.ColorBurn),
    LINEAR_BURN(R.string.blend_linear_burn, null),
    DARKER_COLOR(R.string.blend_darker_color, null),
    LIGHTEN(R.string.blend_lighten, BlendMode.Lighten),
    SCREEN(R.string.blend_screen, BlendMode.Screen),
    COLOR_DODGE(R.string.blend_color_dodge, BlendMode.ColorDodge),
    LINEAR_DODGE(R.string.blend_linear_dodge, BlendMode.Plus),
    LIGHTER_COLOR(R.string.blend_lighter_color, null),
    OVERLAY(R.string.blend_overlay, BlendMode.Overlay),
    SOFT_LIGHT(R.string.blend_soft_light, BlendMode.Softlight),
    HARD_LIGHT(R.string.blend_hard_light, BlendMode.Hardlight),
    VIVID_LIGHT(R.string.blend_vivid_light, null),
    LINEAR_LIGHT(R.string.blend_linear_light, null),
    PIN_LIGHT(R.string.blend_pin_light, null),
    HARD_MIX(R.string.blend_hard_mix, null),
    DIFFERENCE(R.string.blend_difference, BlendMode.Difference),
    EXCLUSION(R.string.blend_exclusion, BlendMode.Exclusion),
    SUBTRACT(R.string.blend_subtract, null),
    DIVIDE(R.string.blend_divide, null),
    HUE(R.string.blend_hue, BlendMode.Hue),
    SATURATION(R.string.blend_saturation, BlendMode.Saturation),
    COLOR(R.string.blend_color, BlendMode.Color),
    LUMINOSITY(R.string.blend_luminosity, BlendMode.Luminosity),
}

/** One channel of the separable modes, backdrop [b] and source [s] in 0..1. */
private fun channel(mode: LayerBlend, b: Float, s: Float): Float = when (mode) {
    LayerBlend.LINEAR_BURN -> (b + s - 1).coerceAtLeast(0f)
    LayerBlend.VIVID_LIGHT -> if (s <= 0.5f) {
        if (s == 0f) 0f else (1 - (1 - b) / (2 * s)).coerceAtLeast(0f)
    } else {
        if (s == 1f) 1f else (b / (2 * (1 - s))).coerceAtMost(1f)
    }
    LayerBlend.LINEAR_LIGHT -> (b + 2 * s - 1).coerceIn(0f, 1f)
    LayerBlend.PIN_LIGHT -> if (s <= 0.5f) minOf(b, 2 * s) else maxOf(b, 2 * s - 1)
    LayerBlend.HARD_MIX -> if (channel(LayerBlend.VIVID_LIGHT, b, s) < 0.5f) 0f else 1f
    LayerBlend.SUBTRACT -> (b - s).coerceAtLeast(0f)
    LayerBlend.DIVIDE -> if (s == 0f) (if (b == 0f) 0f else 1f) else (b / s).coerceAtMost(1f)
    else -> s
}

private fun luminosity(c: FloatArray) = 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2]

/**
 * Composites source [s] onto backdrop [b] (both non-premultiplied RGBA, 0..1) in one of the
 * CPU modes, returning the non-premultiplied result. [noise] (0..1, stable per pixel) decides
 * Dissolve: the source shows fully where its opacity beats the noise, not at all elsewhere.
 * The blended colour is mixed in by the source's coverage, as the PDF/W3C compositing model does.
 */
fun blendPixel(mode: LayerBlend, b: FloatArray, s: FloatArray, noise: Float = 0f): FloatArray {
    if (mode == LayerBlend.DISSOLVE) return if (s[3] > noise) floatArrayOf(s[0], s[1], s[2], 1f).let { sourceOver(b, it) } else b.copyOf()
    val mixed = when (mode) {
        LayerBlend.DARKER_COLOR -> if (luminosity(s) < luminosity(b)) s.copyOf(3) else b.copyOf(3)
        LayerBlend.LIGHTER_COLOR -> if (luminosity(s) > luminosity(b)) s.copyOf(3) else b.copyOf(3)
        else -> FloatArray(3) { channel(mode, b[it], s[it]) }
    }
    // Where the backdrop is transparent the source shows as itself.
    val blended = FloatArray(4) { i -> if (i == 3) s[3] else (1 - b[3]) * s[i] + b[3] * mixed[i] }
    return sourceOver(b, blended)
}

/** Source-over of non-premultiplied colours. */
private fun sourceOver(b: FloatArray, s: FloatArray): FloatArray {
    val a = s[3] + b[3] * (1 - s[3])
    if (a <= 0f) return FloatArray(4)
    return FloatArray(4) { i -> if (i == 3) a else (s[i] * s[3] + b[i] * b[3] * (1 - s[3])) / a }
}

/** A stable pseudo-random value in [0, 1) per pixel, for Dissolve, so it doesn't shimmer. */
internal fun dissolveNoise(x: Int, y: Int): Float {
    var h = x * 374761393 + y * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return (abs(h xor (h ushr 16)) % 65536) / 65536f
}
