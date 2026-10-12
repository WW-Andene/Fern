package com.andene.fern.canvas

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * One node of a path built with the Bézier pen tool (roadmap item 96): where the path passes,
 * and the handle pulled out of it. The handle on the other side mirrors it (a smooth node);
 * a node whose handle sits on it is a corner.
 */
data class BezierNode(val point: WorldPoint, val handleOut: WorldPoint = point) {
    val handleIn: WorldPoint get() = WorldPoint(2 * point.x - handleOut.x, 2 * point.y - handleOut.y)
    val isCorner: Boolean get() = handleOut == point

    fun shiftedBy(offset: WorldPoint) = BezierNode(point - offset, handleOut - offset)
}

/**
 * The polyline through [nodes]' cubic segments, closed back to the first node when [closed],
 * with segments sampled about every [spacing] world units (at least 4 samples each). Corner
 * nodes between segments are doubled, so strokes drawn through them keep a sharp corner (see
 * withSharpCorners in Shapes.kt).
 */
fun flattenBezier(nodes: List<BezierNode>, closed: Boolean, spacing: Double): List<WorldPoint> {
    if (nodes.isEmpty()) return emptyList()
    if (nodes.size == 1) return listOf(nodes[0].point)
    val segments = if (closed) nodes.size else nodes.size - 1
    val out = ArrayList<WorldPoint>()
    out.add(nodes[0].point)
    for (i in 0 until segments) {
        val a = nodes[i]
        val b = nodes[(i + 1) % nodes.size]
        val p0 = a.point
        val p1 = a.handleOut
        val p2 = b.handleIn
        val p3 = b.point
        // Control-polygon length bounds the curve's length: enough samples for the spacing.
        val length = hypot(p1.x - p0.x, p1.y - p0.y) + hypot(p2.x - p1.x, p2.y - p1.y) + hypot(p3.x - p2.x, p3.y - p2.y)
        val samples = maxOf(4, ceil(length / spacing.coerceAtLeast(1e-300)).toInt().coerceAtMost(256))
        for (s in 1..samples) {
            val t = s.toDouble() / samples
            val u = 1 - t
            val x = u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x
            val y = u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y
            out.add(WorldPoint(x, y))
        }
        // A corner where two segments meet is doubled. The path's own ends need nothing: drawing
        // starts and stops exactly on them, so they stay sharp.
        if (b.isCorner && i < segments - 1) out.add(p3)
    }
    return out
}
