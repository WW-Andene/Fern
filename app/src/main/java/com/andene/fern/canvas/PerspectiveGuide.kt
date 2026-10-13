package com.andene.fern.canvas

import com.andene.fern.R
import androidx.annotation.StringRes
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Perspective drawing guide, like Procreate's and Clip Studio's: one or two vanishing points
 * with construction lines radiating from them, and assisted drawing that straightens each pen
 * stroke onto the perspective direction it starts out closest to.
 * - [ONE_POINT]: lines toward one vanishing point, plus horizontals and verticals (a road or
 *   corridor seen head-on).
 * - [TWO_POINT]: lines toward either of two vanishing points on the horizon, plus verticals
 *   (a building seen from a corner).
 * - [THREE_POINT]: two vanishing points on the horizon and a third above or below it, so
 *   verticals converge too (a tower seen from the ground, a street from above; roadmap item 344).
 */
enum class PerspectiveMode(@StringRes val label: Int, val pointCount: Int) {
    OFF(R.string.perspective_off, 0),
    ONE_POINT(R.string.perspective_one, 1),
    TWO_POINT(R.string.perspective_two, 2),
    THREE_POINT(R.string.perspective_three, 3),
}

/**
 * Unit directions a stroke starting at [start] may follow under [mode]: toward each of
 * [vanishingPoints], plus the vertical, plus the horizontal in one-point perspective (in
 * two-point, horizontals recede to the vanishing points instead). A vanishing point at [start]
 * itself gives no direction and is skipped.
 */
fun perspectiveDirections(mode: PerspectiveMode, vanishingPoints: List<WorldPoint>, start: WorldPoint): List<WorldPoint> {
    if (mode == PerspectiveMode.OFF) return emptyList()
    val directions = mutableListOf<WorldPoint>()
    for (vp in vanishingPoints.take(mode.pointCount)) {
        val dx = vp.x - start.x
        val dy = vp.y - start.y
        val length = hypot(dx, dy)
        if (length > 1e-9) directions.add(WorldPoint(dx / length, dy / length))
    }
    // In three-point perspective verticals recede to the third point, so there is no plain vertical.
    if (mode != PerspectiveMode.THREE_POINT) directions.add(WorldPoint(0.0, 1.0))
    if (mode == PerspectiveMode.ONE_POINT) directions.add(WorldPoint(1.0, 0.0))
    return directions
}

/**
 * The candidate direction best matching the movement from [start] to [current]: the one whose
 * line (either way along it) makes the smallest angle with that movement. Null if there are
 * no candidates or no movement yet.
 */
fun closestDirection(candidates: List<WorldPoint>, start: WorldPoint, current: WorldPoint): WorldPoint? {
    val mx = current.x - start.x
    val my = current.y - start.y
    val length = hypot(mx, my)
    if (length < 1e-12) return null
    return candidates.maxByOrNull { abs((it.x * mx + it.y * my) / length) }
}

/** [point] projected onto the line through [origin] along unit [direction]. */
fun projectOntoLine(origin: WorldPoint, direction: WorldPoint, point: WorldPoint): WorldPoint {
    val t = (point.x - origin.x) * direction.x + (point.y - origin.y) * direction.y
    return WorldPoint(origin.x + direction.x * t, origin.y + direction.y * t)
}
