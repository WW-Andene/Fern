package com.andene.fern.canvas

import com.andene.fern.R
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The shape a [PenType.STAMP] brush repeats along its path. */
enum class StampTip(@StringRes val label: Int) {
    CIRCLE(R.string.tip_circle),
    SOFT(R.string.tip_soft),
    SQUARE(R.string.tip_square),
    STAR(R.string.tip_star),
    /** A greyscale picture chosen by the user ([StampBrush.mask]); a circle until one is chosen. */
    IMAGE(R.string.tip_image),
}

/** Side length of an image tip's coverage grid: enough detail for a dab, small enough to store per stroke. */
const val TIP_MASK_SIZE = 64

/**
 * An image brush tip: [size] x [size] ink coverage values, row-major (0 = none, 255 = full).
 * Stored on the brush, so each stroke keeps the picture it was drawn with.
 */
class TipMask(val size: Int, val alpha: ByteArray) {
    init {
        require(size > 0 && alpha.size == size * size) { "Tip mask must be size x size" }
    }

    override fun equals(other: Any?): Boolean = other is TipMask && other.size == size && other.alpha.contentEquals(alpha)

    override fun hashCode(): Int = 31 * size + alpha.contentHashCode()
}

/**
 * Turns a picture ([pixels] ARGB, [width] x [height]) into a tip: fitted into a square without
 * stretching, box-averaged down to [size]. A picture with transparency uses its alpha as the
 * ink; an opaque one uses its darkness, so a black-on-white scan of a mark becomes that mark.
 */
fun tipMaskFromArgb(pixels: IntArray, width: Int, height: Int, size: Int = TIP_MASK_SIZE): TipMask {
    require(width > 0 && height > 0 && pixels.size >= width * height)
    val hasAlpha = pixels.any { (it ushr 24) != 0xFF }
    val side = maxOf(width, height).toDouble()
    // The picture's offset inside the square it's centred in.
    val offsetX = (side - width) / 2.0
    val offsetY = (side - height) / 2.0
    val out = ByteArray(size * size)
    for (ty in 0 until size) for (tx in 0 until size) {
        val x0 = tx * side / size - offsetX
        val x1 = (tx + 1) * side / size - offsetX
        val y0 = ty * side / size - offsetY
        val y1 = (ty + 1) * side / size - offsetY
        var sum = 0.0
        var count = 0
        for (y in kotlin.math.floor(y0).toInt() until kotlin.math.ceil(y1).toInt()) for (x in kotlin.math.floor(x0).toInt() until kotlin.math.ceil(x1).toInt()) {
            count++
            if (x !in 0 until width || y !in 0 until height) continue // padding: no ink
            val c = pixels[y * width + x]
            val a = (c ushr 24) / 255.0
            sum += if (hasAlpha) a else {
                val luminance = (0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)) / 255.0
                1.0 - luminance
            }
        }
        out[ty * size + tx] = if (count == 0) 0 else (sum / count * 255.0 + 0.5).toInt().coerceIn(0, 255).toByte()
    }
    return TipMask(size, out)
}

/**
 * Settings of a stamp brush: a tip shape repeated along the stroke, the way Procreate's and
 * Krita's brush engines build a stroke from "dabs". Stored on each [PenType.STAMP] stroke, so
 * editing the brush later doesn't change what was already drawn.
 *
 * All fractions are relative to the stroke width, so a brush looks the same at any size/zoom.
 */
data class StampBrush(
    val tip: StampTip = StampTip.CIRCLE,
    /** Distance between stamps, as a fraction of the width (0.25 = four stamps per width). */
    val spacing: Float = 0.5f,
    /** How far each stamp may stray sideways from the path, as a fraction of the width. */
    val scatter: Float = 0f,
    /** How much smaller a stamp may randomly be (0 = all the same size, 1 = down to nothing). */
    val sizeJitter: Float = 0f,
    /** Random rotation of each stamp, as a fraction of a full turn. */
    val angleJitter: Float = 0f,
    /** Rotate each stamp to the direction of travel, so oriented tips follow the stroke. */
    val followDirection: Boolean = false,
    /** The picture of a [StampTip.IMAGE] tip; null for the drawn tips. */
    val mask: TipMask? = null,
) {
    companion object {
        const val MIN_SPACING = 0.05f
        const val MAX_SPACING = 2f
        const val MAX_SCATTER = 2f
    }

    /** The same brush with every value pulled into its valid range (e.g. after reading a file). */
    fun clamped(): StampBrush = copy(
        spacing = spacing.coerceIn(MIN_SPACING, MAX_SPACING),
        scatter = scatter.coerceIn(0f, MAX_SCATTER),
        sizeJitter = sizeJitter.coerceIn(0f, 1f),
        angleJitter = angleJitter.coerceIn(0f, 1f),
    )
}

/** A stamp brush saved under a name in the brush editor, for reuse across documents. */
data class BrushPreset(val name: String, val brush: StampBrush)

/** One placed stamp in screen space: where, how big (diameter), and rotated by how much (radians). */
data class Stamp(val center: Offset, val size: Float, val angle: Float)

// Upper bound per stroke so a huge, tightly spaced stroke can't stall a frame.
private const val MAX_STAMPS = 16384

/**
 * Where to put each stamp of a stroke whose screen-space path is [points], at [widthScreen]
 * base size, with [widthFactorAt] scaling the size per source point (pressure/tilt). Stamps sit
 * every `spacing x width` along the path's arc length, continuing across points, so density
 * doesn't depend on how fast the points were captured. Jitter comes from a hash of the stamp
 * number, never a live random generator, so the stroke is identical every frame (no shimmer)
 * and in every export.
 */
fun stampPlacements(
    points: List<Offset>,
    widthScreen: Float,
    brush: StampBrush,
    widthFactorAt: (Int) -> Float = { 1f },
): List<Stamp> {
    if (points.isEmpty() || widthScreen <= 0f) return emptyList()
    val step = (brush.spacing.coerceIn(StampBrush.MIN_SPACING, StampBrush.MAX_SPACING) * widthScreen).coerceAtLeast(0.5f)
    val stamps = ArrayList<Stamp>()
    fun place(center: Offset, direction: Float, index: Int) {
        val n = stamps.size
        val size = widthScreen * widthFactorAt(index) * (1f - brush.sizeJitter * stampHash(n, 0))
        val scatter = (stampHash(n, 1) * 2f - 1f) * brush.scatter * widthScreen
        // Scatter is perpendicular to the direction of travel.
        val offset = Offset(-sin(direction), cos(direction)) * scatter
        val angle = (if (brush.followDirection) direction else 0f) + (stampHash(n, 2) * 2f - 1f) * brush.angleJitter * PI.toFloat()
        stamps.add(Stamp(center + offset, size, angle))
    }
    if (points.size == 1) {
        place(points[0], 0f, 0)
        return stamps
    }
    // Distance still to travel before the next stamp; the first stamp sits on the first point.
    var untilNext = 0f
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val length = hypot(b.x - a.x, b.y - a.y)
        if (length == 0f) continue
        val direction = atan2(b.y - a.y, b.x - a.x)
        var travelled = untilNext
        while (travelled <= length) {
            if (stamps.size >= MAX_STAMPS) return stamps
            place(a + (b - a) * (travelled / length), direction, i)
            travelled += step
        }
        untilNext = travelled - length
    }
    if (stamps.isEmpty()) place(points[0], 0f, 0)
    return stamps
}

/** A stable pseudo-random value in [0, 1) from two integers (integer hash, no state). */
private fun stampHash(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263 + 0x5bd1e995
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF) / 2147483648f
}

/** The five outer and five inner points of a star of diameter [size] centred on the origin, pointing up at angle 0. */
fun starOutline(size: Float, angle: Float): List<Offset> = (0 until 10).map { k ->
    val radius = if (k % 2 == 0) size / 2f else size / 5f
    val theta = angle - PI.toFloat() / 2f + k * PI.toFloat() / 5f
    Offset(cos(theta) * radius, sin(theta) * radius)
}
