package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How a variable-width outline ends: a half-disc ([ROUND]) or a half-width square extension ([SQUARE]). */
enum class OutlineCap { ROUND, SQUARE }

// Half-disc caps as 8 segments: smooth at stroke widths a pen produces, cheap to fill.
private const val CAP_SEGMENTS = 8

// Longest a bend's offset may grow, as a multiple of the half-width (SVG's default miter limit is 4; 2 keeps sharp zigzags tidy).
private const val MITER_LIMIT = 2f

/**
 * The closed outline of a stroke whose width varies along its length: the centreline [points]
 * offset to each side by [halfWidths] (one per point) along the local normal, joined into one
 * polygon with [cap] ends. Filled once (non-zero winding), it renders a pressure or calligraphy
 * stroke as a single shape, so - unlike drawing each segment as its own line - translucent ink
 * isn't darkened where segments overlap at every point.
 *
 * The normal at an interior point is the average of its two segments' normals (zero-length
 * segments are skipped), lengthened as a miter up to [MITER_LIMIT] so bends keep their width,
 * and the sides stay continuous through bends. Very sharp turns can make
 * one side fold over itself; non-zero filling still covers that area exactly once.
 *
 * Returns an empty list for fewer than two distinct points; the caller draws a dot instead.
 */
fun variableWidthOutline(points: List<Offset>, halfWidths: FloatArray, cap: OutlineCap): List<Offset> {
    require(halfWidths.size == points.size) { "One half-width per point" }
    // Unit direction of each segment, carrying the previous one over zero-length segments.
    val directions = arrayOfNulls<Offset>(points.size - 1)
    var last: Offset? = null
    for (i in 0 until points.size - 1) {
        val d = points[i + 1] - points[i]
        val length = hypot(d.x, d.y)
        if (length > 1e-6f) last = Offset(d.x / length, d.y / length)
        directions[i] = last
    }
    val firstDirection = directions.firstOrNull { it != null } ?: return emptyList()
    // Fill leading gaps (zero-length segments before the first real one).
    for (i in directions.indices) if (directions[i] == null) directions[i] = firstDirection else break

    val left = ArrayList<Offset>(points.size)
    val right = ArrayList<Offset>(points.size)
    for (i in points.indices) {
        val before = directions.getOrNull(i - 1) ?: directions[0]!!
        val after = directions.getOrNull(i) ?: directions.last()!!
        var tx = before.x + after.x
        var ty = before.y + after.y
        val tLength = hypot(tx, ty)
        // A full reversal leaves no average direction; use the incoming one.
        if (tLength < 1e-6f) { tx = before.x; ty = before.y } else { tx /= tLength; ty /= tLength }
        // Offsetting along the averaged normal narrows a bend by cos(half the turn); scaling by its
        // inverse (a miter) keeps the full width there, capped so near-reversals don't spike.
        val cosHalfTurn = (tx * after.x + ty * after.y).coerceAtLeast(1f / MITER_LIMIT)
        val normal = Offset(-ty, tx) * (halfWidths[i] / cosHalfTurn)
        left.add(points[i] + normal)
        right.add(points[i] - normal)
    }

    val startDirection = directions[0]!!
    val endDirection = directions.last()!!
    val outline = ArrayList<Offset>(points.size * 2 + 2 * (CAP_SEGMENTS + 1))
    outline.addAll(left)
    addCap(outline, points.last(), endDirection, halfWidths.last(), cap)
    for (i in right.indices.reversed()) outline.add(right[i])
    addCap(outline, points.first(), -startDirection, halfWidths.first(), cap)
    return outline
}

/**
 * Appends the cap around [end], going from the left side to the right side as seen walking in
 * [direction] (the direction leaving the stroke at that end).
 */
private fun addCap(outline: MutableList<Offset>, end: Offset, direction: Offset, halfWidth: Float, cap: OutlineCap) {
    when (cap) {
        OutlineCap.ROUND -> {
            // Sweep the half-disc from the left normal, through the tip, to the right normal.
            val base = atan2(direction.y, direction.x) + (PI / 2).toFloat()
            for (k in 1 until CAP_SEGMENTS) {
                val angle = base - (PI.toFloat() * k / CAP_SEGMENTS)
                outline.add(end + Offset(cos(angle), sin(angle)) * halfWidth)
            }
        }
        OutlineCap.SQUARE -> {
            val normal = Offset(-direction.y, direction.x) * halfWidth
            val extension = direction * halfWidth
            outline.add(end + normal + extension)
            outline.add(end - normal + extension)
        }
    }
}
