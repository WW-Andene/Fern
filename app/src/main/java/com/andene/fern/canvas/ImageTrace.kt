package com.andene.fern.canvas

import kotlin.math.hypot

/** Longest edge, in pixels, an image is traced at; larger images are sampled down to it. */
const val TRACE_MAX_EDGE_PX = 512

/**
 * Image trace (roadmap item 101): the dark shapes of an image as filled outlines. [argb] holds
 * [width] x [height] pixels; a pixel is ink when its luminance is under the threshold Otsu's
 * method picks from the image's own histogram (transparent pixels are paper). Each outline is a
 * closed loop in pixel units (0..width, 0..height), simplified to within [tolerance] pixels;
 * holes are joined into the outline around them by a zero-width cut, so each shape fills as
 * one polygon under the non-zero rule. Shapes smaller than [minArea] square pixels (specks)
 * are dropped. Empty for an image with no ink.
 */
fun traceImage(argb: IntArray, width: Int, height: Int, tolerance: Double = 1.0, minArea: Double = 4.0): List<List<WorldPoint>> {
    if (width <= 0 || height <= 0) return emptyList()
    val luminance = IntArray(width * height) { i ->
        val c = argb[i]
        val alpha = c ushr 24
        if (alpha < 128) 255 else {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
    }
    val threshold = otsuThreshold(luminance)
    val ink = BooleanArray(width * height) { luminance[it] < threshold }
    val loops = boundaryLoops(ink, width, height).map { simplifyLoop(it, tolerance) }.filter { abs(signedArea(it)) >= minArea }
    // Walking with ink on the right makes outer outlines and holes turn opposite ways.
    val (outers, holes) = loops.partition { signedArea(it) > 0 }
    return outers.map { outer ->
        holes.filter { polygonContains(outer, it[0]) && abs(signedArea(it)) < abs(signedArea(outer)) }
            .fold(outer) { joined, hole -> bridgeLoop(joined, hole) }
            .let { it + it.first() } // closed, like the strokes it becomes
    }
}

/** Otsu's threshold: the grey level that best splits the histogram into two classes. */
private fun otsuThreshold(levels: IntArray): Int {
    val histogram = IntArray(256)
    for (v in levels) histogram[v.coerceIn(0, 255)]++
    val total = levels.size.toDouble()
    val sum = (0..255).sumOf { it * histogram[it].toDouble() }
    var sumBack = 0.0
    var weightBack = 0.0
    var best = 0.0
    var threshold = 128
    for (t in 0..255) {
        weightBack += histogram[t]
        if (weightBack == 0.0) continue
        val weightFore = total - weightBack
        if (weightFore == 0.0) break
        sumBack += t * histogram[t].toDouble()
        val meanBack = sumBack / weightBack
        val meanFore = (sum - sumBack) / weightFore
        val between = weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore)
        if (between > best) {
            best = between
            threshold = t + 1 // ink is strictly darker than the split
        }
    }
    return threshold
}

/**
 * Every boundary loop of the set cells in [grid], walked along cell edges with the set cells on
 * the right (diagonal neighbours count as apart): outer outlines one way round, holes the
 * other. Every cell side between a set and an unset cell is one directed edge of exactly one
 * loop; each unwalked one starts a new loop.
 */
private fun boundaryLoops(grid: BooleanArray, w: Int, h: Int): List<List<WorldPoint>> {
    fun at(x: Int, y: Int) = x in 0 until w && y in 0 until h && grid[y * w + x]
    // An edge is its start corner (corner (x, y) is cell (x, y)'s top-left) and direction
    // (0 up, 1 right, 2 down, 3 left).
    val visited = HashSet<Long>()
    fun key(x: Int, y: Int, dir: Int) = ((y.toLong() * (w + 1) + x) shl 2) or dir.toLong()
    val dxs = intArrayOf(0, 1, 0, -1)
    val dys = intArrayOf(-1, 0, 1, 0)
    val loops = ArrayList<List<WorldPoint>>()
    fun walk(startX: Int, startY: Int, startDir: Int) {
        if (key(startX, startY, startDir) in visited) return
        var cx = startX
        var cy = startY
        var dir = startDir
        val loop = ArrayList<WorldPoint>()
        while (visited.add(key(cx, cy, dir))) {
            loop.add(WorldPoint(cx.toDouble(), cy.toDouble()))
            cx += dxs[dir]
            cy += dys[dir]
            // The two cells ahead of the new corner, left and right of the heading.
            val left: Boolean
            val right: Boolean
            when (dir) {
                0 -> { left = at(cx - 1, cy - 1); right = at(cx, cy - 1) }
                1 -> { left = at(cx, cy - 1); right = at(cx, cy) }
                2 -> { left = at(cx, cy); right = at(cx - 1, cy) }
                else -> { left = at(cx - 1, cy); right = at(cx - 1, cy - 1) }
            }
            dir = when {
                !right -> (dir + 1) % 4 // the set region ends: turn right round its corner
                left -> (dir + 3) % 4 // it continues across: turn left along it
                else -> dir
            }
        }
        loops.add(loop)
    }
    for (y in 0 until h) for (x in 0 until w) {
        if (!at(x, y)) continue
        if (!at(x, y - 1)) walk(x, y, 1) // top side, heading right
        if (!at(x + 1, y)) walk(x + 1, y, 2) // right side, heading down
        if (!at(x, y + 1)) walk(x + 1, y + 1, 3) // bottom side, heading left
        if (!at(x - 1, y)) walk(x, y + 1, 0) // left side, heading up
    }
    return loops
}

/** Signed area (shoelace); positive for loops walked clockwise on screen (y down). */
private fun signedArea(loop: List<WorldPoint>): Double {
    var area = 0.0
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[(i + 1) % loop.size]
        area += a.x * b.y - b.x * a.y
    }
    return area / 2
}

private fun abs(v: Double) = kotlin.math.abs(v)

/**
 * Douglas-Peucker on a closed loop (split at the point farthest from the start). Points exactly
 * in line with their neighbours go first, so the loop starts on a real corner: the start is
 * always kept, and a start partway along an edge could otherwise cost a nearby corner.
 */
private fun simplifyLoop(loop: List<WorldPoint>, tolerance: Double): List<WorldPoint> {
    val points = loop.filterIndexed { i, p ->
        val a = loop[(i - 1 + loop.size) % loop.size]
        val b = loop[(i + 1) % loop.size]
        (p.x - a.x) * (b.y - a.y) - (p.y - a.y) * (b.x - a.x) != 0.0
    }.ifEmpty { loop }
    if (points.size <= 4) return points
    val closed = points + points.first()
    val keep = BooleanArray(closed.size)
    fun distance(p: WorldPoint, a: WorldPoint, b: WorldPoint): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length2 = dx * dx + dy * dy
        if (length2 == 0.0) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / length2).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
    fun simplify(from: Int, to: Int) {
        var worst = -1
        var worstDistance = tolerance
        for (i in from + 1 until to) {
            val d = distance(closed[i], closed[from], closed[to])
            if (d > worstDistance) {
                worstDistance = d
                worst = i
            }
        }
        if (worst >= 0) {
            keep[worst] = true
            simplify(from, worst)
            simplify(worst, to)
        }
    }
    val far = points.indices.maxBy { hypot(points[it].x - points[0].x, points[it].y - points[0].y) }
    keep[0] = true
    keep[far] = true
    simplify(0, far)
    simplify(far, closed.lastIndex)
    return points.indices.filter { keep[it] }.map { points[it] }
}

/** [outer] with [hole] spliced in at their closest points, there and back along one cut. */
private fun bridgeLoop(outer: List<WorldPoint>, hole: List<WorldPoint>): List<WorldPoint> {
    var bestI = 0
    var bestJ = 0
    var best = Double.MAX_VALUE
    for (i in outer.indices) for (j in hole.indices) {
        val d = hypot(outer[i].x - hole[j].x, outer[i].y - hole[j].y)
        if (d < best) {
            best = d
            bestI = i
            bestJ = j
        }
    }
    return outer.subList(0, bestI + 1) + hole.subList(bestJ, hole.size) + hole.subList(0, bestJ + 1) + outer.subList(bestI, outer.size)
}
