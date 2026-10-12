package com.andene.fern.canvas

import kotlin.math.abs

/**
 * A connector (roadmap item 68): an arrow from one text label or sticky note to another, the
 * edges of a flowchart or mind map. It names its ends by [TextItem.id], so it follows them
 * wherever they're moved and redraws between their current boxes. A connector whose label is
 * gone (deleted) isn't drawn; undoing the deletion brings both back.
 */
data class Connector(val id: String, val fromId: String, val toId: String)

/**
 * The visible segment of a connector between two boxes ([min] and [max] corners each): the
 * line between their centres, cut where it leaves the first box and enters the second, so the
 * arrow runs edge to edge. Null when the boxes overlap so much that no gap is left to draw.
 */
fun connectorSegment(
    fromMin: WorldPoint,
    fromMax: WorldPoint,
    toMin: WorldPoint,
    toMax: WorldPoint,
): Pair<WorldPoint, WorldPoint>? {
    val a = WorldPoint((fromMin.x + fromMax.x) / 2, (fromMin.y + fromMax.y) / 2)
    val b = WorldPoint((toMin.x + toMax.x) / 2, (toMin.y + toMax.y) / 2)
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0.0 && dy == 0.0) return null
    // Fraction of the way from a to b at which the line leaves a box centred on [center] with
    // half-sizes [hx], [hy] (centred on a for the start; the end uses the same from b's side).
    fun exitFraction(hx: Double, hy: Double): Double {
        val tx = if (dx == 0.0) Double.POSITIVE_INFINITY else hx / abs(dx)
        val ty = if (dy == 0.0) Double.POSITIVE_INFINITY else hy / abs(dy)
        return minOf(tx, ty)
    }
    val start = exitFraction((fromMax.x - fromMin.x) / 2, (fromMax.y - fromMin.y) / 2)
    val end = 1.0 - exitFraction((toMax.x - toMin.x) / 2, (toMax.y - toMin.y) / 2)
    if (start >= end) return null
    return WorldPoint(a.x + dx * start, a.y + dy * start) to WorldPoint(a.x + dx * end, a.y + dy * end)
}

/**
 * Flowchart cleanup: new top-left positions for the labels joined by [connectors], laid out in
 * layers (a simple Sugiyama-style layout). Each label's layer is its longest path from a root
 * (a label nothing points to; in a cycle, the earliest-placed label stands in), so arrows run
 * downward; layers are rows [rowGap] apart, labels in a row [columnGap] apart and centred on
 * the diagram's original centre, keeping their order by current x. Labels not in any
 * connector aren't moved. [sizes] gives each label's width and height.
 */
fun tidyDiagram(
    positions: Map<String, WorldPoint>,
    sizes: Map<String, Pair<Double, Double>>,
    connectors: List<Connector>,
    columnGap: Double,
    rowGap: Double,
): Map<String, WorldPoint> {
    val edges = connectors.filter { it.fromId in positions && it.toId in positions && it.fromId != it.toId }
    val nodes = edges.flatMap { listOf(it.fromId, it.toId) }.distinct()
    if (nodes.isEmpty()) return emptyMap()
    val layer = HashMap<String, Int>()
    // Longest-path layering by repeated relaxation, bounded so a cycle can't loop forever.
    val roots = nodes.filter { n -> edges.none { it.toId == n } }.ifEmpty { listOf(nodes.minBy { positions.getValue(it).y }) }
    roots.forEach { layer[it] = 0 }
    repeat(nodes.size) {
        var changed = false
        for (e in edges) {
            val from = layer[e.fromId] ?: continue
            if ((layer[e.toId] ?: -1) < from + 1 && from + 1 < nodes.size) {
                layer[e.toId] = from + 1
                changed = true
            }
        }
        if (!changed) return@repeat
    }
    nodes.forEach { layer.putIfAbsent(it, 0) }
    val centerX = nodes.map { positions.getValue(it).x + sizes.getValue(it).first / 2 }.average()
    val top = nodes.minOf { positions.getValue(it).y }
    val result = HashMap<String, WorldPoint>()
    var y = top
    for ((_, row) in nodes.groupBy { layer.getValue(it) }.toSortedMap()) {
        val ordered = row.sortedBy { positions.getValue(it).x }
        val width = ordered.sumOf { sizes.getValue(it).first } + columnGap * (ordered.size - 1)
        var x = centerX - width / 2
        for (id in ordered) {
            result[id] = WorldPoint(x, y)
            x += sizes.getValue(id).first + columnGap
        }
        y += ordered.maxOf { sizes.getValue(it).second } + rowGap
    }
    return result
}
