package com.andene.fern.canvas

import kotlin.math.hypot

import kotlin.math.abs

/** True if [points] forms a (roughly) closed loop: its first and last points are within [tolerance] of each other. */
fun isClosedLoop(points: List<WorldPoint>, tolerance: Double): Boolean {
    if (points.size < 3) return false
    val first = points.first()
    val last = points.last()
    val dx = last.x - first.x
    val dy = last.y - first.y
    return dx * dx + dy * dy <= tolerance * tolerance
}

/** Even-odd point-in-polygon test (ray casting). [points] need not be explicitly closed (first != last is fine). */
fun polygonContains(points: List<WorldPoint>, point: WorldPoint): Boolean {
    var inside = false
    var j = points.size - 1
    for (i in points.indices) {
        val a = points[i]
        val b = points[j]
        if ((a.y > point.y) != (b.y > point.y)) {
            val intersectX = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            if (point.x < intersectX) inside = !inside
        }
        j = i
    }
    return inside
}

/** Shoelace formula: the polygon's absolute area, used to pick the innermost enclosing shape when several overlap. */
fun polygonArea(points: List<WorldPoint>): Double {
    var sum = 0.0
    var j = points.size - 1
    for (i in points.indices) {
        sum += (points[j].x + points[i].x) * (points[j].y - points[i].y)
        j = i
    }
    return abs(sum) / 2.0
}

/** Shortest distance from [point] to the polyline through [points] (to the single point if there's only one). */
fun distanceToPolyline(points: List<WorldPoint>, point: WorldPoint): Double {
    if (points.isEmpty()) return Double.POSITIVE_INFINITY
    if (points.size == 1) return hypot(point.x - points[0].x, point.y - points[0].y)
    var best = Double.POSITIVE_INFINITY
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
        best = minOf(best, hypot(point.x - (a.x + t * dx), point.y - (a.y + t * dy)))
    }
    return best
}

/**
 * Moving-average smoothing that keeps the point count (so per-point pressure/tilt stay paired
 * by index) and pins both endpoints. Each pass replaces every interior point with the average
 * of itself and its neighbours; more passes give a smoother line.
 */
fun smoothPoints(points: List<WorldPoint>, passes: Int): List<WorldPoint> {
    if (points.size < 3) return points
    var current = points
    repeat(passes) {
        current = current.mapIndexed { i, p ->
            if (i == 0 || i == current.size - 1) p
            else WorldPoint(
                (current[i - 1].x + p.x + current[i + 1].x) / 3.0,
                (current[i - 1].y + p.y + current[i + 1].y) / 3.0,
            )
        }
    }
    return current
}

/** [count] points evenly spaced by arc length along the polyline through [points] (endpoints kept). */
fun resamplePolyline(points: List<WorldPoint>, count: Int): List<WorldPoint> {
    if (points.size < 2 || count < 2) return List(count.coerceAtLeast(1)) { points.firstOrNull() ?: WorldPoint.Zero }
    val cumulative = DoubleArray(points.size)
    for (i in 1 until points.size) {
        cumulative[i] = cumulative[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    }
    val total = cumulative.last()
    if (total == 0.0) return List(count) { points.first() }
    var segment = 1
    return List(count) { i ->
        val target = total * i / (count - 1)
        while (segment < points.size - 1 && cumulative[segment] < target) segment++
        val span = cumulative[segment] - cumulative[segment - 1]
        val t = if (span == 0.0) 0.0 else (target - cumulative[segment - 1]) / span
        val a = points[segment - 1]
        val b = points[segment]
        WorldPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
}

/**
 * Indices of the points of [points] to keep so consecutive kept points are at least
 * [minSpacing] apart: a point closer than that to the last kept one is skipped. The first and
 * last points are always kept, so endpoints never move. Each decision depends only on earlier
 * points, so appending points to a stroke never changes which earlier ones are kept (no flicker
 * while drawing). Used for zoom-aware level of detail, with the spacing set to one screen pixel.
 */
fun decimatePolyline(points: List<WorldPoint>, minSpacing: Double): IntArray {
    if (points.size <= 2 || !(minSpacing > 0.0)) return IntArray(points.size) { it }
    val minSpacingSq = minSpacing * minSpacing
    val kept = IntArray(points.size)
    var count = 0
    kept[count++] = 0
    var last = points[0]
    for (i in 1 until points.size - 1) {
        val p = points[i]
        val dx = p.x - last.x
        val dy = p.y - last.y
        if (dx * dx + dy * dy >= minSpacingSq) {
            kept[count++] = i
            last = p
        }
    }
    kept[count++] = points.size - 1
    return kept.copyOf(count)
}
