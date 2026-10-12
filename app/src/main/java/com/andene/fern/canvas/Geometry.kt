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
