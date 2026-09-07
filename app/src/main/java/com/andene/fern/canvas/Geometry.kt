package com.andene.fern.canvas

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
