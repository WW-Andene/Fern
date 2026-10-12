package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The shape a [PenType.STAMP] brush repeats along its path. */
enum class StampTip(val label: String) {
    CIRCLE("Circle"),
    SOFT("Soft"),
    SQUARE("Square"),
    STAR("Star"),
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
