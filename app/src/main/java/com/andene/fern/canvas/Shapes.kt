package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
enum class ShapeKind { LINE, RECTANGLE, ELLIPSE, ARROW, SPEECH_BUBBLE, THOUGHT_BUBBLE, SHOUT_BUBBLE, ROUNDED_RECTANGLE, POLYGON, STAR, SPIRAL }

/**
 * Settings of the newer shape kinds (roadmap item 221), chosen in the shape tool's properties:
 * a polygon's sides and a star's points ([sides], 3 to 12).
 */
object ShapeOptions {
    var sides by androidx.compose.runtime.mutableIntStateOf(6)
    const val MIN_SIDES = 3
    const val MAX_SIDES = 12
}

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
    ShapeKind.RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.SPEECH_BUBBLE, ShapeKind.THOUGHT_BUBBLE, ShapeKind.SHOUT_BUBBLE,
    ShapeKind.ROUNDED_RECTANGLE, ShapeKind.POLYGON, ShapeKind.STAR, ShapeKind.SPIRAL -> snapAspect(start, end)
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
        withSharpCorners(
            listOf(
                topLeft,
                WorldPoint(bottomRight.x, topLeft.y),
                bottomRight,
                WorldPoint(topLeft.x, bottomRight.y),
                topLeft,
            )
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

    ShapeKind.SPEECH_BUBBLE -> {
        // A comic speech balloon: an ellipse filling the dragged box, with a tail leaving the
        // lower-left of the outline and pointing down-left beyond the box, toward the speaker.
        val centerX = (start.x + end.x) / 2.0
        val centerY = (start.y + end.y) / 2.0
        val radiusX = abs(end.x - start.x) / 2.0
        val radiusY = abs(end.y - start.y) / 2.0
        fun onEllipse(angle: Double) = WorldPoint(centerX + radiusX * cos(angle), centerY + radiusY * sin(angle))
        // The tail spans 110-130 degrees (y grows downward, so that's lower-left).
        val tailStart = Math.toRadians(110.0)
        val tailEnd = Math.toRadians(130.0)
        val tip = WorldPoint(centerX - radiusX * 1.1, centerY + radiusY * 1.5)
        val outline = (0..ELLIPSE_SEGMENTS).map { 2.0 * PI * it / ELLIPSE_SEGMENTS }
            .filter { it <= tailStart || it >= tailEnd }
        val before = outline.filter { it <= tailStart }.map(::onEllipse)
        val after = outline.filter { it >= tailEnd }.map(::onEllipse)
        before + withSharpCorners(listOf(before.last(), onEllipse(tailStart), tip, onEllipse(tailEnd), after.first())).drop(1).dropLast(1) + after
    }

    // Comic balloons (roadmap item 105); a thought balloon's trailing circles are [shapeExtras].
    ShapeKind.THOUGHT_BUBBLE -> thoughtCloudPoints(start, end)
    ShapeKind.SHOUT_BUBBLE -> shoutBurstPoints(start, end)

    // Newer kinds (roadmap item 221), each inscribed in the dragged box.
    ShapeKind.ROUNDED_RECTANGLE -> roundedRectanglePoints(start, end)
    ShapeKind.POLYGON -> starPoints(start, end, ShapeOptions.sides, inner = 1.0)
    ShapeKind.STAR -> starPoints(start, end, ShapeOptions.sides, inner = STAR_INNER_RATIO)
    ShapeKind.SPIRAL -> spiralPoints(start, end)

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
        withSharpCorners(listOf(start, end, leftWing, end, rightWing))
    }
}

/**
 * Further outlines a shape is drawn with besides its main one (roadmap item 105): a thought
 * balloon's trailing circles. Empty for every other shape. They are committed as their own
 * strokes, grouped with the main one.
 */
fun shapeExtras(kind: ShapeKind, start: WorldPoint, end: WorldPoint): List<List<WorldPoint>> = when (kind) {
    ShapeKind.THOUGHT_BUBBLE -> thoughtTrail(start, end)
    else -> emptyList()
}

/**
 * [points] with every interior vertex doubled. Strokes are drawn as curves through the
 * midpoints between points (see drawStroke), which rounds a lone vertex into a wide bend; a
 * doubled vertex is the curve's end and the next one's start, so the outline meets it in a
 * sharp corner and the edges between corners stay straight.
 */
private fun withSharpCorners(points: List<WorldPoint>): List<WorldPoint> =
    points.flatMapIndexed { i, p -> if (i == 0 || i == points.lastIndex) listOf(p) else listOf(p, p) }

private fun rotatedOffset(from: WorldPoint, ux: Double, uy: Double, angle: Double, length: Double): WorldPoint {
    val rx = ux * cos(angle) - uy * sin(angle)
    val ry = ux * sin(angle) + uy * cos(angle)
    return WorldPoint(from.x + rx * length, from.y + ry * length)
}

// Recognition thresholds, relative to the stroke's own size so they work at any zoom level.
private const val LINE_MAX_DEVIATION_RATIO = 0.06 // max distance from the chord, as a fraction of its length
private const val LOOP_CLOSE_RATIO = 0.2 // end-to-start gap allowed, as a fraction of the bounding-box diagonal
private const val RECTANGLE_MIN_FILL = 0.88 // polygon area / bounding-box area; a perfect rectangle is 1.0
private const val ELLIPSE_MIN_FILL = 0.65 // a perfect ellipse is pi/4 = 0.785

private const val POLYGON_MAX_DEVIATION_RATIO = 0.03 // max distance from the simplified outline, as a fraction of the box diagonal;
// tight enough that a circle (a hexagon through it strays 4.7% of the diagonal) stays an ellipse
private const val POLYGON_MAX_CORNERS = 6
private const val ARC_MAX_DEVIATION_RATIO = 0.06 // max distance from the fitted circle, as a fraction of its radius
private const val ARC_MIN_POINTS = 8
private const val ARC_MIN_SAGITTA_RATIO = 0.08 // an arc flatter than this (sagitta / chord) is a line

/**
 * QuickShape-style recognition of a freehand stroke (roadmap items 28 and 118): returns clean
 * points for the shape it most resembles, or null if it doesn't clearly match any. Open strokes
 * become a line, a polyline of up to [POLYGON_MAX_CORNERS] straight segments, or a circular
 * arc; closed ones an axis-aligned rectangle, a polygon (triangle, rotated quadrilateral,
 * pentagon, hexagon) or an ellipse. Lines, rectangles and ellipses use the SHAPE tool's
 * generators and snapping, so a recognised circle or 45° line comes out exactly as if drawn
 * with that tool.
 */
fun recognizeShape(points: List<WorldPoint>): List<WorldPoint>? {
    if (points.size < 3) return null
    val first = points.first()
    val last = points.last()
    val chord = hypot(last.x - first.x, last.y - first.y)
    if (chord > 0.0 && points.all { distanceToPolyline(listOf(first, last), it) <= chord * LINE_MAX_DEVIATION_RATIO }) {
        return generateShapePoints(ShapeKind.LINE, first, snappedShapeEnd(ShapeKind.LINE, first, last))
    }
    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }
    val diagonal = hypot(maxX - minX, maxY - minY)
    if (diagonal <= 0.0) return null
    if (!isClosedLoop(points, diagonal * LOOP_CLOSE_RATIO)) {
        // The arc first: a smooth curve also simplifies to a few straight segments.
        recognizeArc(points)?.let { return it }
        return recognizePolyline(points, diagonal, closed = false)
    }
    val boxArea = (maxX - minX) * (maxY - minY)
    if (boxArea <= 0.0) return null
    val fill = abs(polygonArea(points)) / boxArea
    if (fill >= RECTANGLE_MIN_FILL) {
        val start = WorldPoint(minX, minY)
        return generateShapePoints(ShapeKind.RECTANGLE, start, snappedShapeEnd(ShapeKind.RECTANGLE, start, WorldPoint(maxX, maxY)))
    }
    recognizePolyline(points, diagonal, closed = true)?.let { return it }
    if (fill >= ELLIPSE_MIN_FILL) {
        val start = WorldPoint(minX, minY)
        return generateShapePoints(ShapeKind.ELLIPSE, start, snappedShapeEnd(ShapeKind.ELLIPSE, start, WorldPoint(maxX, maxY)))
    }
    return null
}

/**
 * Straight segments through the stroke's corners, when every point lies close to them: the
 * corners are found by Ramer-Douglas-Peucker simplification. A closed outline's corners are
 * found from its farthest point from the start, so the start (rarely a real corner) drops out.
 */
private fun recognizePolyline(points: List<WorldPoint>, diagonal: Double, closed: Boolean): List<WorldPoint>? {
    val tolerance = diagonal * POLYGON_MAX_DEVIATION_RATIO
    val corners = if (closed) {
        val far = points.indices.maxBy { hypot(points[it].x - points[0].x, points[it].y - points[0].y) }
        val loop = points.subList(far, points.size) + points.subList(0, far + 1)
        simplify(loop, tolerance).dropLast(1)
    } else {
        simplify(points, tolerance)
    }
    // A closed outline needs three corners (a triangle); an open one three points (one bend).
    if (corners.size < 3 || corners.size > POLYGON_MAX_CORNERS + if (closed) 0 else 1) return null
    val outline = if (closed) corners + corners.first() else corners
    if (points.any { distanceToPolyline(outline, it) > tolerance }) return null
    // Segments far shorter than the shape are hand wobble, not sides.
    if (outline.zipWithNext().any { (a, b) -> hypot(b.x - a.x, b.y - a.y) < diagonal * 0.1 }) return null
    return withSharpCorners(outline)
}

/** Ramer-Douglas-Peucker: the fewest points of [points] that keep every point within [tolerance]. */
private fun simplify(points: List<WorldPoint>, tolerance: Double): List<WorldPoint> {
    if (points.size < 3) return points
    val first = points.first()
    val last = points.last()
    var farthest = -1
    var farthestDistance = tolerance
    for (i in 1 until points.lastIndex) {
        val d = distanceToPolyline(listOf(first, last), points[i])
        if (d > farthestDistance) {
            farthest = i
            farthestDistance = d
        }
    }
    if (farthest == -1) return listOf(first, last)
    return simplify(points.subList(0, farthest + 1), tolerance).dropLast(1) + simplify(points.subList(farthest, points.size), tolerance)
}

/**
 * A circular arc through the stroke's ends and its middle point, when every point lies close
 * to that circle and the stroke bends enough not to be a line.
 */
private fun recognizeArc(points: List<WorldPoint>): List<WorldPoint>? {
    // Any three points lie on a circle; it takes more samples for the fit to mean anything.
    if (points.size < ARC_MIN_POINTS) return null
    val a = points.first()
    val m = points[points.size / 2]
    val b = points.last()
    val center = circumcenter(a, m, b) ?: return null
    val radius = hypot(a.x - center.x, a.y - center.y)
    if (points.any { abs(hypot(it.x - center.x, it.y - center.y) - radius) > radius * ARC_MAX_DEVIATION_RATIO }) return null
    val chord = hypot(b.x - a.x, b.y - a.y)
    if (chord > 0.0 && distanceToPolyline(listOf(a, b), m) < chord * ARC_MIN_SAGITTA_RATIO) return null
    val start = atan2(a.y - center.y, a.x - center.x)
    val through = atan2(m.y - center.y, m.x - center.x)
    val end = atan2(b.y - center.y, b.x - center.x)
    // Sweep from start to end the way that passes through the middle point.
    fun ccw(from: Double, to: Double) = ((to - from) % (2 * PI) + 2 * PI) % (2 * PI)
    val sweep = if (ccw(start, through) <= ccw(start, end)) ccw(start, end) else ccw(start, end) - 2 * PI
    val segments = (abs(sweep) / (2 * PI) * ELLIPSE_SEGMENTS).toInt().coerceAtLeast(4)
    return (0..segments).map {
        val angle = start + sweep * it / segments
        WorldPoint(center.x + radius * cos(angle), center.y + radius * sin(angle))
    }
}

/** Centre of the circle through three points, or null when they're (nearly) collinear. */
private fun circumcenter(a: WorldPoint, b: WorldPoint, c: WorldPoint): WorldPoint? {
    val d = 2 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
    if (abs(d) < 1e-12) return null
    val a2 = a.x * a.x + a.y * a.y
    val b2 = b.x * b.x + b.y * b.y
    val c2 = c.x * c.x + c.y * c.y
    return WorldPoint(
        (a2 * (b.y - c.y) + b2 * (c.y - a.y) + c2 * (a.y - b.y)) / d,
        (a2 * (c.x - b.x) + b2 * (a.x - c.x) + c2 * (b.x - a.x)) / d,
    )
}

/**
 * Live adjustment of a recognised shape while the pen stays down (QuickShape's "keep holding
 * to edit"): a line's far end follows the pen; any other shape scales and rotates about its
 * centre by how the pen has moved relative to that centre since the shape snapped.
 */
fun adjustRecognizedShape(clean: List<WorldPoint>, snappedAt: WorldPoint, pen: WorldPoint): List<WorldPoint> {
    if (clean.size == 2) return listOf(clean[0], snappedShapeEnd(ShapeKind.LINE, clean[0], pen))
    val cx = clean.sumOf { it.x } / clean.size
    val cy = clean.sumOf { it.y } / clean.size
    val from = hypot(snappedAt.x - cx, snappedAt.y - cy)
    if (from < 1e-9) return clean
    val factor = hypot(pen.x - cx, pen.y - cy) / from
    val turn = atan2(pen.y - cy, pen.x - cx) - atan2(snappedAt.y - cy, snappedAt.x - cx)
    val c = cos(turn) * factor
    val s = sin(turn) * factor
    return clean.map { WorldPoint(cx + (it.x - cx) * c - (it.y - cy) * s, cy + (it.x - cx) * s + (it.y - cy) * c) }
}

// A star's inner points sit at this share of its outer radius; the classic five-pointed star's ratio.
private const val STAR_INNER_RATIO = 0.382
private const val SPIRAL_TURNS = 3
private const val SPIRAL_SEGMENTS_PER_TURN = 48
private const val CORNER_SEGMENTS = 8

/**
 * A regular polygon or star in the box from [start] to [end]: [count] outer points, pointing up,
 * with an inner point between each pair at [inner] of the radius (1 for a plain polygon).
 */
private fun starPoints(start: WorldPoint, end: WorldPoint, count: Int, inner: Double): List<WorldPoint> {
    val cx = (start.x + end.x) / 2
    val cy = (start.y + end.y) / 2
    val rx = abs(end.x - start.x) / 2
    val ry = abs(end.y - start.y) / 2
    val n = count.coerceIn(ShapeOptions.MIN_SIDES, ShapeOptions.MAX_SIDES)
    val steps = if (inner < 1.0) n * 2 else n
    val points = (0 until steps).map { k ->
        val angle = -PI / 2 + 2 * PI * k / steps
        val r = if (inner < 1.0 && k % 2 == 1) inner else 1.0
        WorldPoint(cx + rx * r * cos(angle), cy + ry * r * sin(angle))
    }
    return withSharpCorners(points + points.first())
}

/** A rectangle with round corners: radius 0.24 x its shorter side, the CLAUDE.md §7.3 proportion. */
private fun roundedRectanglePoints(start: WorldPoint, end: WorldPoint): List<WorldPoint> {
    val left = minOf(start.x, end.x)
    val right = maxOf(start.x, end.x)
    val top = minOf(start.y, end.y)
    val bottom = maxOf(start.y, end.y)
    val r = 0.24 * minOf(right - left, bottom - top)
    fun corner(cx: Double, cy: Double, from: Double) = (0..CORNER_SEGMENTS).map { k ->
        val a = from + PI / 2 * k / CORNER_SEGMENTS
        WorldPoint(cx + r * cos(a), cy + r * sin(a))
    }
    val outline = corner(right - r, top + r, -PI / 2) + corner(right - r, bottom - r, 0.0) + corner(left + r, bottom - r, PI / 2) + corner(left + r, top + r, PI)
    return outline + outline.first()
}

/** An Archimedean spiral from the box's centre out to its edge in [SPIRAL_TURNS] turns. */
private fun spiralPoints(start: WorldPoint, end: WorldPoint): List<WorldPoint> {
    val cx = (start.x + end.x) / 2
    val cy = (start.y + end.y) / 2
    val rx = abs(end.x - start.x) / 2
    val ry = abs(end.y - start.y) / 2
    val total = SPIRAL_TURNS * SPIRAL_SEGMENTS_PER_TURN
    return (0..total).map { k ->
        val t = k.toDouble() / total
        val angle = 2 * PI * SPIRAL_TURNS * t
        WorldPoint(cx + rx * t * cos(angle), cy + ry * t * sin(angle))
    }
}
