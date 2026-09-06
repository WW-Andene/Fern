package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * A single freehand stroke, stored entirely in world space (i.e. independent of
 * the current pan/zoom). Points are appended live while the user is drawing.
 */
class Stroke(
    val color: Color,
    val widthWorld: Float,
) {
    private val _points = mutableListOf<Offset>()
    val points: List<Offset> get() = _points

    // Cached bounding box in world space, expanded as points are added.
    var minX: Float = Float.POSITIVE_INFINITY; private set
    var minY: Float = Float.POSITIVE_INFINITY; private set
    var maxX: Float = Float.NEGATIVE_INFINITY; private set
    var maxY: Float = Float.NEGATIVE_INFINITY; private set

    fun addPoint(point: Offset) {
        _points.add(point)
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
    }

    /** True if this stroke's bounding box (padded by its width) intersects the given world-space rect. */
    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (points.isEmpty()) return false
        val pad = widthWorld
        return !(maxX + pad < left || minX - pad > right || maxY + pad < top || minY - pad > bottom)
    }
}
