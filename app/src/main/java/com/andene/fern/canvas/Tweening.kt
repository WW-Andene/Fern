package com.andene.fern.canvas

import kotlin.math.hypot

// A stroke counts as closed when its ends are this close, as a fraction of its bounding-box diagonal.
private const val CLOSED_GAP_RATIO = 0.05

/**
 * Which stroke of one key frame morphs into which of the next (roadmap item 82): nearest
 * centres first, each stroke used once, so a frame redrawn in a different order still tweens
 * each shape into its counterpart. Strokes left without a partner (unequal counts) are left
 * out. Pairs come in [start]'s drawing order.
 */
fun tweenPairs(start: List<Stroke>, end: List<Stroke>): List<Pair<Stroke, Stroke>> {
    fun center(s: Stroke) = WorldPoint((s.minX + s.maxX) / 2, (s.minY + s.maxY) / 2)
    val startCenters = start.map(::center)
    val endCenters = end.map(::center)
    val candidates = ArrayList<Triple<Double, Int, Int>>(start.size * end.size)
    for (i in start.indices) for (j in end.indices) {
        candidates.add(Triple(hypot(startCenters[i].x - endCenters[j].x, startCenters[i].y - endCenters[j].y), i, j))
    }
    // Ties keep drawing order (stable sort), so identical layouts pair as drawn.
    candidates.sortBy { it.first }
    val partner = IntArray(start.size) { -1 }
    val taken = BooleanArray(end.size)
    for ((_, i, j) in candidates) {
        if (partner[i] == -1 && !taken[j]) {
            partner[i] = j
            taken[j] = true
        }
    }
    return start.indices.filter { partner[it] != -1 }.map { start[it] to end[partner[it]] }
}

/**
 * [a] and [b] resampled to [count] points each, matched point to point for morphing. Two
 * closed outlines (a circle, a loop) are also lined up: [b]'s starting point and direction are
 * chosen to bring its points nearest [a]'s, so the tween doesn't twist round on the way.
 */
fun tweenSamples(a: Stroke, b: Stroke, count: Int): Pair<List<WorldPoint>, List<WorldPoint>> {
    val pa = resamplePolyline(a.points, count)
    val pb = resamplePolyline(b.points, count)
    if (count < 4 || !isClosed(a) || !isClosed(b)) return pa to pb
    // Cyclic without the repeated closing point.
    val ring = pb.dropLast(1)
    val n = ring.size
    var best = Double.MAX_VALUE
    var bestShift = 0
    var bestReversed = false
    for (reversed in listOf(false, true)) for (shift in 0 until n) {
        var cost = 0.0
        for (i in 0 until n) {
            val q = ring[if (reversed) ((shift - i) % n + n) % n else (shift + i) % n]
            val dx = pa[i].x - q.x
            val dy = pa[i].y - q.y
            cost += dx * dx + dy * dy
            if (cost >= best) break
        }
        if (cost < best) {
            best = cost
            bestShift = shift
            bestReversed = reversed
        }
    }
    val aligned = List(n) { i -> ring[if (bestReversed) ((bestShift - i) % n + n) % n else (bestShift + i) % n] }
    return pa to aligned + aligned.first()
}

private fun isClosed(s: Stroke): Boolean {
    val points = s.points
    if (points.size < 3) return false
    val diagonal = hypot(s.maxX - s.minX, s.maxY - s.minY)
    return diagonal > 0 && hypot(points.first().x - points.last().x, points.first().y - points.last().y) <= diagonal * CLOSED_GAP_RATIO
}
