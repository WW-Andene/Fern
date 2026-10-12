package com.andene.fern.canvas

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/** Most cells along one side of the gap-fill grid; past this the cells grow instead. */
const val GAP_FILL_MAX_CELLS = 1024

/**
 * Gap-closing bucket fill (roadmap item 94): the outline of the area around [seed] enclosed by
 * [walls], treating gaps narrower than about twice [gapWorld] as closed, within the rectangle
 * [minX]..[maxX] x [minY]..[maxY] (the view). Null when the area isn't enclosed there (the fill
 * would reach the rectangle's edge) or the seed lies on ink.
 *
 * Works on a grid of [cell]-sized cells (grown if the rectangle would need more than
 * [GAP_FILL_MAX_CELLS] a side): the walls are drawn in, each thickened by [gapWorld] so small
 * openings close; the free cells reachable from the seed are flooded; the flooded area is
 * grown back by [gapWorld], so the fill runs under the ink instead of stopping short of it;
 * and its outer boundary is traced and simplified into a polygon. Islands inside the area are
 * covered too - the fill goes beneath the ink, so lines drawn there stay visible.
 */
fun gapClosingFill(
    walls: List<Stroke>,
    seed: WorldPoint,
    minX: Double,
    minY: Double,
    maxX: Double,
    maxY: Double,
    cell: Double,
    gapWorld: Double,
): List<WorldPoint>? {
    if (!(maxX > minX && maxY > minY && cell > 0.0)) return null
    val size = maxOf(cell, (maxX - minX) / GAP_FILL_MAX_CELLS, (maxY - minY) / GAP_FILL_MAX_CELLS)
    val w = ceil((maxX - minX) / size).toInt().coerceAtLeast(1)
    val h = ceil((maxY - minY) / size).toInt().coerceAtLeast(1)
    val sx = floor((seed.x - minX) / size).toInt()
    val sy = floor((seed.y - minY) / size).toInt()
    if (sx !in 0 until w || sy !in 0 until h) return null

    // 1. Walls, thickened by the gap tolerance.
    val wall = BooleanArray(w * h)
    for (stroke in walls) {
        val points = stroke.points
        if (points.isEmpty()) continue
        val radius = (stroke.widthWorld / 2 + gapWorld) / size
        for (i in points.indices) {
            val a = points[i]
            val b = if (i + 1 < points.size) points[i + 1] else a
            val length = hypot(b.x - a.x, b.y - a.y) / size
            val steps = maxOf(1, ceil(length / maxOf(0.5, radius / 2)).toInt())
            for (step in 0..steps) {
                val t = step.toDouble() / steps
                stampDisk(wall, w, h, (a.x + (b.x - a.x) * t - minX) / size, (a.y + (b.y - a.y) * t - minY) / size, radius)
            }
        }
    }
    if (wall[sy * w + sx]) return null

    // 2. Flood the free cells from the seed; reaching the edge means the area is open.
    val filled = BooleanArray(w * h)
    val queue = IntArray(w * h)
    var head = 0
    var tail = 0
    filled[sy * w + sx] = true
    queue[tail++] = sy * w + sx
    while (head < tail) {
        val c = queue[head++]
        val x = c % w
        val y = c / w
        if (x == 0 || y == 0 || x == w - 1 || y == h - 1) return null
        for (n in intArrayOf(c - 1, c + 1, c - w, c + w)) {
            if (!filled[n] && !wall[n]) {
                filled[n] = true
                queue[tail++] = n
            }
        }
    }

    // 3. Grow back under the ink by the gap tolerance (not the ink's own width: the fill only
    //    needs to meet the line's middle, which the thickening moved it away from).
    val grow = gapWorld / size
    val region = if (grow > 0.0) BooleanArray(w * h).also { grown ->
        for (i in 0 until tail) {
            val c = queue[i]
            stampDisk(grown, w, h, c % w + 0.5, c / w + 0.5, grow)
            grown[c] = true
        }
    } else filled

    // 4. Outer boundary as a polygon in world units.
    val outline = traceOuterBoundary(region, w, h) ?: return null
    return simplifyPolygon(outline, 0.75).map { (x, y) -> WorldPoint(minX + x * size, minY + y * size) }
}

private fun stampDisk(grid: BooleanArray, w: Int, h: Int, cx: Double, cy: Double, radius: Double) {
    val r = maxOf(radius, 0.5)
    val x0 = floor(cx - r).toInt().coerceAtLeast(0)
    val x1 = ceil(cx + r).toInt().coerceAtMost(w - 1)
    val y0 = floor(cy - r).toInt().coerceAtLeast(0)
    val y1 = ceil(cy + r).toInt().coerceAtMost(h - 1)
    val r2 = r * r
    for (y in y0..y1) {
        val dy = y + 0.5 - cy
        for (x in x0..x1) {
            val dx = x + 0.5 - cx
            if (dx * dx + dy * dy <= r2) grid[y * w + x] = true
        }
    }
}

/**
 * The outer boundary of the set cells of [grid] (one connected region here: everything set was
 * grown from the flood), as a loop of cell-corner coordinates, walked with the region on the
 * right. Starts on the top edge of the first set cell in scan order, which is always on the
 * outer boundary (nothing of the region lies above it). Diagonal neighbours count as apart,
 * matching the 4-connected flood. Null for an empty grid.
 */
private fun traceOuterBoundary(grid: BooleanArray, w: Int, h: Int): List<Pair<Double, Double>>? {
    fun at(x: Int, y: Int) = x in 0 until w && y in 0 until h && grid[y * w + x]
    val first = grid.indexOfFirst { it }
    if (first < 0) return null
    // Corner (x, y) is the top-left corner of cell (x, y). Head right along the cell's top edge.
    val startX = first % w
    val startY = first / w
    var cx = startX
    var cy = startY
    var dir = 1 // 0 up, 1 right, 2 down, 3 left
    val dxs = intArrayOf(0, 1, 0, -1)
    val dys = intArrayOf(-1, 0, 1, 0)
    val loop = ArrayList<Pair<Double, Double>>()
    val limit = 4 * (w + 1) * (h + 1)
    var steps = 0
    do {
        loop.add(cx.toDouble() to cy.toDouble())
        cx += dxs[dir]
        cy += dys[dir]
        // The two cells ahead of the new corner, on the left and on the right of the heading.
        val left: Boolean
        val right: Boolean
        when (dir) {
            0 -> { left = at(cx - 1, cy - 1); right = at(cx, cy - 1) }
            1 -> { left = at(cx, cy - 1); right = at(cx, cy) }
            2 -> { left = at(cx, cy); right = at(cx - 1, cy) }
            else -> { left = at(cx - 1, cy); right = at(cx - 1, cy - 1) }
        }
        dir = when {
            !right -> (dir + 1) % 4 // the region ends: turn right around its corner
            left -> (dir + 3) % 4 // the region continues across: turn left along it
            else -> dir
        }
        if (++steps > limit) return null
    } while (cx != startX || cy != startY || dir != 1)
    return loop
}

/**
 * The closed loop [points] simplified by Douglas-Peucker: every dropped point is within
 * [tolerance] of the simplified outline (the cell staircase becomes straight edges and smooth
 * diagonals). Returned closed (first point repeated at the end).
 */
private fun simplifyPolygon(points: List<Pair<Double, Double>>, tolerance: Double): List<Pair<Double, Double>> {
    val closed = points + points.first()
    if (points.size <= 3) return closed
    fun distance(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val length2 = dx * dx + dy * dy
        if (length2 == 0.0) return hypot(p.first - a.first, p.second - a.second)
        val t = (((p.first - a.first) * dx + (p.second - a.second) * dy) / length2).coerceIn(0.0, 1.0)
        return hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy))
    }
    val keep = BooleanArray(closed.size)
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
    // The loop's start and end are the same point, so split it at the point farthest from the
    // start and simplify the two halves.
    val far = points.indices.maxBy { hypot(points[it].first - points[0].first, points[it].second - points[0].second) }
    keep[0] = true
    keep[far] = true
    keep[closed.lastIndex] = true
    simplify(0, far)
    simplify(far, closed.lastIndex)
    return closed.indices.filter { keep[it] }.map { closed[it] }
}
