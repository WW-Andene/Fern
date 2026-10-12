package com.andene.fern.canvas

// Strokes with at most this many points show every point as a node; denser ones show this many,
// evenly spread, so a freehand stroke's hundreds of points don't become hundreds of handles.
const val MAX_EDIT_NODES = 24

/**
 * Indices of a stroke's points that get node handles in node editing: every point for a short
 * polyline (shapes, straight segments), otherwise [MAX_EDIT_NODES] spread evenly by index,
 * always including both ends.
 */
fun editNodeIndices(pointCount: Int): List<Int> {
    if (pointCount <= 0) return emptyList()
    if (pointCount <= MAX_EDIT_NODES) return List(pointCount) { it }
    return List(MAX_EDIT_NODES) { k -> ((pointCount - 1).toLong() * k / (MAX_EDIT_NODES - 1)).toInt() }.distinct()
}

/**
 * [original] with node [nodes]`[nodeIndex]` moved by [delta]. The points between it and its
 * neighbouring nodes follow with a smooth falloff (1 at the node, 0 at the neighbours, eased),
 * so dragging a node of a freehand stroke bends the stroke instead of pulling one point into a
 * spike; with every point a node (a shape), only that point moves.
 */
fun dragNode(original: List<WorldPoint>, nodes: List<Int>, nodeIndex: Int, delta: WorldPoint): List<WorldPoint> {
    val center = nodes[nodeIndex]
    val previous = nodes.getOrNull(nodeIndex - 1) ?: center
    val next = nodes.getOrNull(nodeIndex + 1) ?: center
    return original.mapIndexed { i, p ->
        val weight = when {
            i == center -> 1.0
            i in (previous + 1) until center -> smoothstep((i - previous).toDouble() / (center - previous))
            i in (center + 1) until next -> smoothstep((next - i).toDouble() / (next - center))
            else -> 0.0
        }
        if (weight == 0.0) p else WorldPoint(p.x + delta.x * weight, p.y + delta.y * weight)
    }
}

private fun smoothstep(t: Double): Double = t * t * (3 - 2 * t)
