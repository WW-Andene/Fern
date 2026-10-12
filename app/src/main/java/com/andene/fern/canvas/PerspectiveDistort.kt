package com.andene.fern.canvas

/**
 * Distort (roadmap item 122, Procreate's and Photoshop's corner-pin transform): the box
 * [min]..[max] mapped onto the quadrilateral [corners] (top-left, top-right, bottom-right,
 * bottom-left) by the projective transform that takes the box's corners to them, so straight
 * lines stay straight and a flat picture can be laid onto a surface in perspective. Corners
 * that still form a parallelogram give an affine map. The square-to-quad solution is
 * Heckbert's ("Fundamentals of Texture Mapping and Image Warping", 1989, §2.2.3).
 */
fun perspectiveDistort(point: WorldPoint, min: WorldPoint, max: WorldPoint, corners: List<WorldPoint>): WorldPoint {
    val width = max.x - min.x
    val height = max.y - min.y
    if (width <= 0.0 || height <= 0.0 || corners.size != 4) return point
    val u = (point.x - min.x) / width
    val v = (point.y - min.y) / height
    val (p0, p1, p2, p3) = corners
    val sx = p0.x - p1.x + p2.x - p3.x
    val sy = p0.y - p1.y + p2.y - p3.y
    val g: Double
    val h: Double
    if (kotlin.math.abs(sx) < 1e-12 && kotlin.math.abs(sy) < 1e-12) {
        g = 0.0; h = 0.0
    } else {
        val dx1 = p1.x - p2.x; val dx2 = p3.x - p2.x
        val dy1 = p1.y - p2.y; val dy2 = p3.y - p2.y
        val den = dx1 * dy2 - dx2 * dy1
        if (kotlin.math.abs(den) < 1e-18) return point // corners collapsed onto a line
        g = (sx * dy2 - dx2 * sy) / den
        h = (dx1 * sy - sx * dy1) / den
    }
    val a = p1.x - p0.x + g * p1.x
    val b = p3.x - p0.x + h * p3.x
    val d = p1.y - p0.y + g * p1.y
    val e = p3.y - p0.y + h * p3.y
    val w = g * u + h * v + 1.0
    return WorldPoint((a * u + b * v + p0.x) / w, (d * u + e * v + p0.y) / w)
}

/** The box's own corners, in the order [perspectiveDistort] takes them: an undistorted quad. */
fun boxCorners(min: WorldPoint, max: WorldPoint): List<WorldPoint> =
    listOf(min, WorldPoint(max.x, min.y), max, WorldPoint(min.x, max.y))
