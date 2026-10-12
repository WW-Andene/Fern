package com.andene.fern.canvas

// Polylines with at most this many points are treated as shapes, every vertex a snap anchor
// (a rectangle has 5, an arrow 5, an ellipse 49); longer freehand strokes offer only their ends.
private const val SHAPE_VERTEX_LIMIT = 16

/**
 * The snap anchor nearest to [point] within [radius] among [strokes], or null if none is close
 * enough: both ends of every stroke, and every vertex of short shape-like polylines. Lets a
 * new line or shape start and end exactly on existing ones, the way vector and diagramming
 * tools snap to object points. Strokes whose bounds are further than [radius] are skipped
 * without looking at their points.
 */
fun nearestSnapAnchor(
    strokes: List<Stroke>,
    point: WorldPoint,
    radius: Double,
    /** Which strokes may be snapped to (e.g. only visible ones); checked after the cheap bounds test. */
    include: (Stroke) -> Boolean = { true },
): WorldPoint? {
    var best: WorldPoint? = null
    var bestDistanceSq = radius * radius
    fun consider(anchor: WorldPoint) {
        val dx = anchor.x - point.x
        val dy = anchor.y - point.y
        val distanceSq = dx * dx + dy * dy
        if (distanceSq <= bestDistanceSq) {
            bestDistanceSq = distanceSq
            best = anchor
        }
    }
    for (stroke in strokes) {
        val points = stroke.points
        if (points.isEmpty()) continue
        if (stroke.maxX < point.x - radius || stroke.minX > point.x + radius || stroke.maxY < point.y - radius || stroke.minY > point.y + radius) continue
        if (!include(stroke)) continue
        if (points.size <= SHAPE_VERTEX_LIMIT) points.forEach(::consider)
        else {
            consider(points.first())
            consider(points.last())
        }
    }
    return best
}
