package com.andene.fern.canvas

import kotlin.math.roundToInt

/**
 * Filters for raster layers and filter (adjustment) layers that change a pixel from those
 * around it (roadmap item 138, Krita's filter layers), unlike the per-pixel [ColorAdjustment]s.
 * Sizes are in world units, so a filter layer looks the same at any zoom; [filter] gets them in
 * pixels of the image it works on.
 */
sealed interface SpatialFilter : Adjustment {
    /** How far (world units) the filter reaches: blur radius, sharpen radius or pixel cell size. */
    val sizeWorld: Double

    /** Filters [argb] (premultiplied ARGB, [width] x [height]) in place, [sizePx] being [sizeWorld] in its pixels. */
    fun filter(argb: IntArray, width: Int, height: Int, sizePx: Int)
}

/** Gaussian blur of radius [sizeWorld] (three box blurs, which approach a Gaussian closely). */
data class GaussianBlur(override val sizeWorld: Double = 4.0) : SpatialFilter {
    override fun filter(argb: IntArray, width: Int, height: Int, sizePx: Int) {
        if (sizePx < 1) return
        // Box widths for three passes approximating a Gaussian of this sigma (W. Jarosz, "Fast Image Convolutions").
        val box = (sizePx / 1.7).roundToInt().coerceAtLeast(1)
        repeat(3) { boxBlur(argb, width, height, box) }
    }
}

/** Sharpen by unsharp mask: each pixel pushed away from its blurred surroundings by [amount], over [sizeWorld]. */
data class Sharpen(override val sizeWorld: Double = 2.0, val amount: Float = 1f) : SpatialFilter {
    override fun filter(argb: IntArray, width: Int, height: Int, sizePx: Int) {
        if (sizePx < 1) return
        val blurred = argb.copyOf()
        GaussianBlur(sizeWorld).filter(blurred, width, height, sizePx)
        for (i in argb.indices) {
            val c = argb[i]
            val a = c ushr 24
            if (a == 0) continue
            fun ch(shift: Int): Int {
                val v = (c shr shift) and 0xFF
                val b = (blurred[i] shr shift) and 0xFF
                return (v + amount * (v - b)).roundToInt().coerceIn(0, a) // premultiplied: never above alpha
            }
            argb[i] = (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

/** Pixelate: the image in square cells of [sizeWorld], each the average of what it covers. */
data class Pixelate(override val sizeWorld: Double = 8.0) : SpatialFilter {
    override fun filter(argb: IntArray, width: Int, height: Int, sizePx: Int) {
        val cell = sizePx.coerceAtLeast(1)
        if (cell == 1) return
        for (cy in 0 until height step cell) for (cx in 0 until width step cell) {
            val x1 = minOf(cx + cell, width)
            val y1 = minOf(cy + cell, height)
            val sum = LongArray(4)
            for (y in cy until y1) for (x in cx until x1) {
                val c = argb[y * width + x]
                sum[0] += (c ushr 24).toLong(); sum[1] += ((c shr 16) and 0xFF).toLong(); sum[2] += ((c shr 8) and 0xFF).toLong(); sum[3] += (c and 0xFF).toLong()
            }
            val n = ((x1 - cx) * (y1 - cy)).toLong()
            val average = ((sum[0] / n).toInt() shl 24) or ((sum[1] / n).toInt() shl 16) or ((sum[2] / n).toInt() shl 8) or (sum[3] / n).toInt()
            for (y in cy until y1) for (x in cx until x1) argb[y * width + x] = average
        }
    }
}

/** One horizontal then vertical box blur of half-width [r] over premultiplied ARGB, edges clamped. */
private fun boxBlur(argb: IntArray, width: Int, height: Int, r: Int) {
    val temp = IntArray(argb.size)
    blurPass(argb, temp, width, height, r, horizontal = true)
    blurPass(temp, argb, width, height, r, horizontal = false)
}

private fun blurPass(src: IntArray, dst: IntArray, width: Int, height: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val span = 2 * r + 1
    for (line in 0 until lines) {
        fun at(k: Int): Int {
            val clamped = k.coerceIn(0, length - 1)
            return if (horizontal) src[line * width + clamped] else src[clamped * width + line]
        }
        var a = 0; var red = 0; var g = 0; var b = 0
        for (k in -r..r) {
            val c = at(k)
            a += c ushr 24; red += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        for (k in 0 until length) {
            val out = ((a / span) shl 24) or ((red / span) shl 16) or ((g / span) shl 8) or (b / span)
            if (horizontal) dst[line * width + k] = out else dst[k * width + line] = out
            val add = at(k + r + 1)
            val drop = at(k - r)
            a += (add ushr 24) - (drop ushr 24)
            red += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
            b += (add and 0xFF) - (drop and 0xFF)
        }
    }
}

/** Invert: every channel turned to its opposite (a negative). */
data object Invert : ColorAdjustment {
    override fun apply(rgb: FloatArray) {
        for (c in 0 until 3) rgb[c] = 1f - rgb[c]
    }
}

/** Posterize: each channel rounded to [levels] evenly spaced values (2..32), for flat poster colour. */
data class Posterize(val levels: Int = 4) : ColorAdjustment {
    override fun apply(rgb: FloatArray) {
        val steps = (levels.coerceIn(2, 32) - 1).toFloat()
        for (c in 0 until 3) rgb[c] = (rgb[c] * steps).roundToInt() / steps
    }
}

/** Threshold: black below [level] of lightness, white above - line art cleanup, stencils. */
data class Threshold(val level: Float = 0.5f) : ColorAdjustment {
    override fun apply(rgb: FloatArray) {
        val luma = 0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]
        val v = if (luma >= level) 1f else 0f
        for (c in 0 until 3) rgb[c] = v
    }
}
