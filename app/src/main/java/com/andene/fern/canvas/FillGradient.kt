package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * A two-colour linear gradient for a fill: from the stroke's own colour to [endColor], running
 * in direction [angleDegrees] (0 = left to right, 90 = top to bottom, clockwise as on screen)
 * across the fill's whole extent in that direction.
 */
data class FillGradient(val endColor: Color, val angleDegrees: Int)

/** The eight directions offered for gradients, in degrees; labels name where the gradient runs to. */
val GRADIENT_DIRECTIONS: List<Pair<Int, String>> = listOf(
    0 to "→", 45 to "↘", 90 to "↓", 135 to "↙", 180 to "←", 225 to "↖", 270 to "↑", 315 to "↗",
)

/**
 * Start and end points of a gradient at [angleDegrees] spanning [points] (the fill's outline):
 * the outline is projected onto the direction, and the gradient runs from its lowest to its
 * highest projection, so the first colour sits exactly at one edge and the second at the other.
 * Works in any space (world or screen), as long as [points] are in it.
 */
fun gradientEndpoints(points: List<Offset>, angleDegrees: Int): Pair<Offset, Offset> {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
    var min = Float.POSITIVE_INFINITY
    var max = Float.NEGATIVE_INFINITY
    var sumX = 0f
    var sumY = 0f
    for (p in points) {
        val t = p.x * direction.x + p.y * direction.y
        if (t < min) min = t
        if (t > max) max = t
        sumX += p.x
        sumY += p.y
    }
    // Any point works as the origin of the line; the centroid keeps the values small.
    val center = Offset(sumX / points.size, sumY / points.size)
    val centerT = center.x * direction.x + center.y * direction.y
    return (center + direction * (min - centerT)) to (center + direction * (max - centerT))
}
