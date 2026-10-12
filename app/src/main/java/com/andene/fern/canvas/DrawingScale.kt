package com.andene.fern.canvas

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** Real-world length units a drawing can be measured in (roadmap item 143), with their size in metres. */
enum class MeasureUnit(val symbol: String, val metres: Double) {
    MILLIMETRE("mm", 0.001),
    CENTIMETRE("cm", 0.01),
    METRE("m", 1.0),
    KILOMETRE("km", 1000.0),
    INCH("in", 0.0254),
    FOOT("ft", 0.3048),
    YARD("yd", 0.9144),
    MILE("mi", 1609.344),
}

/**
 * A document's drawing scale (roadmap item 143), as in Concepts' and Affinity's document units:
 * one world unit (one canvas pixel at 100 % zoom) is [unitsPerWorld] [unit]s. Lengths, areas,
 * rulers, shape readouts and the transform dialog then read in that unit. Documents without a
 * scale measure in world units ("px").
 */
data class DrawingScale(val unit: MeasureUnit, val unitsPerWorld: Double) {
    init {
        require(unitsPerWorld > 0 && unitsPerWorld.isFinite()) { "unitsPerWorld must be positive" }
    }

    companion object {
        /**
         * The scale that makes a line [worldLength] long measure [realLength] [unit]s - the
         * calibration a measured line offers ("this line is 2.5 m"). Null for a zero-length line
         * or a length that isn't positive.
         */
        fun calibrated(worldLength: Double, realLength: Double, unit: MeasureUnit): DrawingScale? =
            if (worldLength > 0 && realLength > 0 && realLength.isFinite()) DrawingScale(unit, realLength / worldLength) else null
    }
}

/** How many measuring units one world unit is: the scale's, or 1 without one. */
val DrawingScale?.factor: Double get() = this?.unitsPerWorld ?: 1.0

/** The unit symbol readouts carry: the scale's, or "px" without one. */
val DrawingScale?.symbol: String get() = this?.unit?.symbol ?: "px"

/** [worldLength] in this scale's unit, with three significant digits (at least whole numbers). */
fun DrawingScale?.formatLength(worldLength: Double): String = "${formatMeasure(worldLength * factor)} $symbol"

/** [worldArea] (square world units) in this scale's unit squared. */
fun DrawingScale?.formatArea(worldArea: Double): String = "${formatMeasure(worldArea * factor * factor)} $symbol²"

/**
 * [value] with enough decimals for three significant digits, none for values of 100 or more:
 * 1234 -> "1234", 12.345 -> "12.3", 0.012345 -> "0.0123". Locale-independent (a dot).
 */
fun formatMeasure(value: Double): String {
    if (value == 0.0 || !value.isFinite()) return "0"
    fun decimals(v: Double) = (2 - kotlin.math.floor(log10(abs(v)))).toInt().coerceIn(0, 6)
    val first = decimals(value)
    val rounded = round(value * 10.0.pow(first)) / 10.0.pow(first)
    // Rounding can carry into the next digit (99.96 -> 100.0), which needs one decimal fewer.
    return String.format(Locale.ROOT, "%.${decimals(rounded)}f", rounded)
}

/** Length of the polyline through [points], closed back to the start when [closed]. */
fun polylineLength(points: List<WorldPoint>, closed: Boolean): Double {
    if (points.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until points.size) total += kotlin.math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    if (closed) total += kotlin.math.hypot(points.first().x - points.last().x, points.first().y - points.last().y)
    return total
}

/**
 * A measurement on the canvas (roadmap item 143): the polyline [points] - a dragged line's two
 * ends, or a stroke's outline - closed back to its start when [closed] (a shape, with an area).
 */
class Measurement(val points: List<WorldPoint>, val closed: Boolean) {
    /** The length along the points (the perimeter when closed). */
    val length: Double get() = polylineLength(points, closed)

    /** The enclosed area when closed; 0 otherwise. */
    val area: Double get() = if (closed) polygonArea(points) else 0.0
}
