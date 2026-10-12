package com.andene.fern.canvas

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * A ruler guide (roadmap item 99): an infinite line pulled out of a screen ruler, [vertical]
 * (at x = [position]) or horizontal (y = [position]), in world units. Moved selections snap
 * to it; saved with the document.
 */
data class Guide(val id: String, val vertical: Boolean, val position: Double)

/** One ruler tick: where (world units) and whether it's a labelled major tick. */
data class RulerTick(val position: Double, val major: Boolean)

/** The smallest gap between labelled ticks on screen, in px; ticks are 1, 2 or 5 times a power of ten apart. */
const val RULER_MIN_MAJOR_SPACING_PX = 64.0

/**
 * The ticks a ruler shows from world [from] to [to] at zoom [scale] (screen px per world unit):
 * labelled ticks 1, 2 or 5 times a power of ten apart - the smallest such step at least
 * [RULER_MIN_MAJOR_SPACING_PX] apart on screen - with four or five unlabelled ones between.
 */
fun rulerTicks(from: Double, to: Double, scale: Double): List<RulerTick> {
    val step = rulerStep(scale)
    // A step of 2 (times a power of ten) splits into quarters; 1 and 5 into fifths.
    val leading = Math.round(step / 10.0.pow(floor(log10(step))))
    val minorCount = if (leading == 2L) 4 else 5
    val minor = step / minorCount
    val first = floor(from / minor).toLong()
    val last = ceil(to / minor).toLong()
    if (last - first > MAX_TICKS) return emptyList()
    return (first..last).map { i ->
        val position = i * minor
        RulerTick(position, Math.floorMod(i, minorCount.toLong()) == 0L)
    }
}

/** The world distance between labelled ticks at zoom [scale]. */
fun rulerStep(scale: Double): Double {
    val minWorld = RULER_MIN_MAJOR_SPACING_PX / scale.coerceAtLeast(1e-300)
    val decade = 10.0.pow(floor(log10(minWorld)))
    return listOf(1.0, 2.0, 5.0, 10.0).map { it * decade }.first { it >= minWorld * (1 - 1e-9) }
}

/** A tick's label: whole numbers plainly, fractions with only the digits the step needs. */
fun rulerLabel(position: Double, step: Double): String {
    val decimals = (-floor(log10(step))).toInt().coerceIn(0, 6)
    return String.format(Locale.ROOT, "%.${decimals}f", position).let { if (it == "-0" || it.matches(Regex("-0\\.0+"))) it.drop(1) else it }
}

private const val MAX_TICKS = 4000
