package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin

/** The shape a [Tool.SHAPE] drag commits, as regular stroke points - see [generateShapePoints]. */
enum class ShapeKind { LINE, RECTANGLE, ELLIPSE, ARROW }

private const val ELLIPSE_SEGMENTS = 48
private const val ANGLE_SNAP_STEP_DEGREES = 15.0
private const val ANGLE_SNAP_TOLERANCE_DEGREES = 5.0
private const val ASPECT_SNAP_TOLERANCE = 0.1 // 10%

/**
 * Snaps a line/arrow's end point to the nearest 15° increment when the drag is already
 * within 5° of one - "shape assist" behavior that helps hit a clean horizontal/vertical/
 * diagonal without forcing every line onto a fixed angle grid.
 */
private fun snapLineEnd(start: WorldPoint, end: WorldPoint): WorldPoint {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx, dy)
    if (length < 1e-9) return end
    val angleDegrees = Math.toDegrees(atan2(dy, dx))
    val nearestStep = round(angleDegrees / ANGLE_SNAP_STEP_DEGREES) * ANGLE_SNAP_STEP_DEGREES
    if (abs(angleDegrees - nearestStep) > ANGLE_SNAP_TOLERANCE_DEGREES) return end
    val snappedRadians = Math.toRadians(nearestStep)
    return WorldPoint(start.x + length * cos(snappedRadians), start.y + length * sin(snappedRadians))
}

/** Snaps a rectangle/ellipse's opposite corner to a square/circle when already within 10% of one. */
private fun snapAspect(start: WorldPoint, end: WorldPoint): WorldPoint {
    val width = end.x - start.x
    val height = end.y - start.y
    if (width == 0.0 || height == 0.0) return end
    val ratio = abs(width) / abs(height)
    if (abs(ratio - 1.0) > ASPECT_SNAP_TOLERANCE) return end
    val side = min(abs(width), abs(height))
    return WorldPoint(start.x + side * sign(width), start.y + side * sign(height))
}

/** The actual end point to use for [kind]'s live preview/final commit, after any relevant snapping. */
fun snappedShapeEnd(kind: ShapeKind, start: WorldPoint, end: WorldPoint): WorldPoint = when (kind) {
    ShapeKind.LINE, ShapeKind.ARROW -> snapLineEnd(start, end)
    ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> snapAspect(start, end)
}

/**
 * Generates the polyline points for [kind] spanning [start] to [end] (already snapped by
 * [snappedShapeEnd]). Every shape becomes one connected polyline so it can be stored and
 * rendered as an ordinary [Stroke] - no special-casing needed anywhere else (undo, erase,
 * select, persistence all already work on strokes).
 */
fun generateShapePoints(kind: ShapeKind, start: WorldPoint, end: WorldPoint): List<WorldPoint> = when (kind) {
    ShapeKind.LINE -> listOf(start, end)

    ShapeKind.RECTANGLE -> {
        val topLeft = WorldPoint(minOf(start.x, end.x), minOf(start.y, end.y))
        val bottomRight = WorldPoint(maxOf(start.x, end.x), maxOf(start.y, end.y))
        listOf(
            topLeft,
            WorldPoint(bottomRight.x, topLeft.y),
            bottomRight,
            WorldPoint(topLeft.x, bottomRight.y),
            topLeft,
        )
    }

    ShapeKind.ELLIPSE -> {
        val centerX = (start.x + end.x) / 2.0
        val centerY = (start.y + end.y) / 2.0
        val radiusX = abs(end.x - start.x) / 2.0
        val radiusY = abs(end.y - start.y) / 2.0
        (0..ELLIPSE_SEGMENTS).map { i ->
            val angle = 2.0 * PI * i / ELLIPSE_SEGMENTS
            WorldPoint(centerX + radiusX * cos(angle), centerY + radiusY * sin(angle))
        }
    }

    ShapeKind.ARROW -> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy).coerceAtLeast(1e-9)
        val ux = dx / length
        val uy = dy / length
        val headLength = (length * 0.2).coerceIn(1.0, length)
        val headAngle = Math.toRadians(25.0)
        // Both wings are reached by walking back from the tip, one continuous line (the
        // path revisits the tip between wings, which draws the same segment twice - visually
        // identical to a proper two-stroke arrowhead, but keeps this a single polyline).
        val leftWing = rotatedOffset(end, -ux, -uy, headAngle, headLength)
        val rightWing = rotatedOffset(end, -ux, -uy, -headAngle, headLength)
        listOf(start, end, leftWing, end, rightWing)
    }
}

private fun rotatedOffset(from: WorldPoint, ux: Double, uy: Double, angle: Double, length: Double): WorldPoint {
    val rx = ux * cos(angle) - uy * sin(angle)
    val ry = ux * sin(angle) + uy * cos(angle)
    return WorldPoint(from.x + rx * length, from.y + ry * length)
}
