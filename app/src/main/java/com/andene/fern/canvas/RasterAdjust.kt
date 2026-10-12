package com.andene.fern.canvas

import kotlin.math.abs
import kotlin.math.pow

/**
 * Colour adjustments for raster layers (roadmap item 92), each a function of one pixel's
 * non-premultiplied colour (0..1), applied to a layer or its selection by
 * [CanvasState.applyAdjustment], and to everything beneath by adjustment layers (item 93).
 */
sealed interface Adjustment {
    /** Adjusts [rgb] (three channels, 0..1) in place. */
    fun apply(rgb: FloatArray)
}

/**
 * Levels: input [inBlack]..[inWhite] stretched to the full range with [gamma] (above 1 lightens
 * the midtones), then compressed into [outBlack]..[outWhite].
 */
data class Levels(
    val inBlack: Float = 0f,
    val inWhite: Float = 1f,
    val gamma: Float = 1f,
    val outBlack: Float = 0f,
    val outWhite: Float = 1f,
) : Adjustment {
    fun map(v: Float): Float {
        val span = (inWhite - inBlack).coerceAtLeast(1e-4f)
        val normalized = ((v - inBlack) / span).coerceIn(0f, 1f)
        val curved = normalized.toDouble().pow(1.0 / gamma.coerceIn(0.1f, 10f)).toFloat()
        return outBlack + curved * (outWhite - outBlack)
    }

    override fun apply(rgb: FloatArray) {
        for (c in 0 until 3) rgb[c] = map(rgb[c])
    }
}

/**
 * Curves: a smooth curve through [points] ((input, output) pairs in 0..1, sorted by input; the
 * ends are pinned at (0,0) and (1,1) unless given), applied to every channel. Monotone cubic
 * (Fritsch-Carlson), so the curve never overshoots between points the way a plain spline can.
 */
data class Curves(val points: List<Pair<Float, Float>> = listOf(0f to 0f, 1f to 1f)) : Adjustment {
    private val table: FloatArray by lazy { buildTable() }

    fun map(v: Float): Float = table[(v.coerceIn(0f, 1f) * (TABLE_SIZE - 1)).toInt()]

    override fun apply(rgb: FloatArray) {
        for (c in 0 until 3) rgb[c] = map(rgb[c])
    }

    private fun buildTable(): FloatArray {
        val pts = (points + listOf(0f to 0f, 1f to 1f).filter { end -> points.none { abs(it.first - end.first) < 1e-6f } })
            .sortedBy { it.first }.distinctBy { it.first }
        val n = pts.size
        val xs = pts.map { it.first.toDouble() }
        val ys = pts.map { it.second.toDouble() }
        val slopes = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(1e-9) }
        val tangents = DoubleArray(n) { i ->
            when (i) {
                0 -> slopes[0]
                n - 1 -> slopes[n - 2]
                else -> if (slopes[i - 1] * slopes[i] <= 0) 0.0 else (slopes[i - 1] + slopes[i]) / 2
            }
        }
        // Fritsch-Carlson: limit tangents so each piece stays monotone.
        for (i in 0 until n - 1) {
            if (slopes[i] == 0.0) {
                tangents[i] = 0.0
                tangents[i + 1] = 0.0
                continue
            }
            val a = tangents[i] / slopes[i]
            val b = tangents[i + 1] / slopes[i]
            val h = a * a + b * b
            if (h > 9) {
                val t = 3 / Math.sqrt(h)
                tangents[i] = t * a * slopes[i]
                tangents[i + 1] = t * b * slopes[i]
            }
        }
        return FloatArray(TABLE_SIZE) { k ->
            val x = k.toDouble() / (TABLE_SIZE - 1)
            val i = (0 until n - 1).firstOrNull { x <= xs[it + 1] } ?: (n - 2)
            val h = xs[i + 1] - xs[i]
            val t = ((x - xs[i]) / h).coerceIn(0.0, 1.0)
            val t2 = t * t
            val t3 = t2 * t
            val y = (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * tangents[i] +
                (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * tangents[i + 1]
            y.toFloat().coerceIn(0f, 1f)
        }
    }

    private companion object {
        const val TABLE_SIZE = 1024
    }
}

/**
 * Hue/saturation/lightness: hue turned by [hueDegrees], saturation scaled by (1 + [saturation])
 * and lightness moved by [lightness] toward white (positive) or black (negative), each -1..1.
 */
data class HueSaturation(val hueDegrees: Float = 0f, val saturation: Float = 0f, val lightness: Float = 0f) : Adjustment {
    override fun apply(rgb: FloatArray) {
        val hsl = rgbToHsl(rgb[0], rgb[1], rgb[2])
        hsl[0] = ((hsl[0] + hueDegrees / 360f) % 1f + 1f) % 1f
        hsl[1] = (hsl[1] * (1 + saturation)).coerceIn(0f, 1f)
        hsl[2] = if (lightness >= 0) hsl[2] + (1 - hsl[2]) * lightness else hsl[2] * (1 + lightness)
        val out = hslToRgb(hsl[0], hsl[1], hsl[2])
        for (c in 0 until 3) rgb[c] = out[c]
    }
}

internal fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2 - max - min) else d / (max + min)
    val h = when (max) {
        r -> (g - b) / d + (if (g < b) 6 else 0)
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    } / 6
    return floatArrayOf(h, s, l)
}

internal fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    if (s == 0f) return floatArrayOf(l, l, l)
    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    fun hue(t0: Float): Float {
        var t = t0
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            t < 1f / 6 -> p + (q - p) * 6 * t
            t < 1f / 2 -> q
            t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6
            else -> p
        }
    }
    return floatArrayOf(hue(h + 1f / 3), hue(h), hue(h - 1f / 3))
}
