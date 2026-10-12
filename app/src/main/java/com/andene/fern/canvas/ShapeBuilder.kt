package com.andene.fern.canvas

import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs

/*
 * Shape builder, knife and contour (roadmap item 141), the Affinity Designer tools of the same
 * names, on top of the platform path operations used by [combineShapes].
 */

/**
 * Every region the closed outlines [shapes] make between them (the shape builder's fragments):
 * each overlap on its own, each part of a shape that nothing overlaps on its own, and each
 * disconnected piece of those separately. Holes are bridged into their outline, as for a filled
 * [combineShapes] result. Fewer than one usable shape gives nothing.
 */
fun divideShapes(shapes: List<List<WorldPoint>>): List<List<WorldPoint>> {
    val nonEmpty = shapes.filter { it.size >= 3 }
    if (nonEmpty.isEmpty()) return emptyList()
    val origin = nonEmpty[0][0]
    var fragments = listOf<Path>()
    val covered = Path()
    for (shape in nonEmpty) {
        val path = pathOf(shape, origin)
        val next = ArrayList<Path>()
        for (fragment in fragments) {
            Path(fragment).also { it.op(path, Path.Op.INTERSECT) }.takeUnless { it.isEmpty }?.let(next::add)
            Path(fragment).also { it.op(path, Path.Op.DIFFERENCE) }.takeUnless { it.isEmpty }?.let(next::add)
        }
        Path(path).also { it.op(covered, Path.Op.DIFFERENCE) }.takeUnless { it.isEmpty }?.let(next::add)
        covered.op(path, Path.Op.UNION)
        fragments = next
    }
    // A fragment can be in several pieces (a shape cut in two by another); each is its own.
    return fragments.flatMap { shapesOf(it, origin, bridgeHoles = true) }
        .filter { polygonArea(it) > MIN_FRAGMENT_AREA }
}

/**
 * The closed outline [shape] cut along the polyline [knife]: the pieces left after a cut
 * [gap] wide (world units) is taken out along the knife, holes bridged. Empty when the knife
 * doesn't cut the shape in two (it missed, or stopped inside it), so the shape stays whole.
 */
fun cutShape(shape: List<WorldPoint>, knife: List<WorldPoint>, gap: Double): List<List<WorldPoint>> {
    if (shape.size < 3 || knife.size < 2) return emptyList()
    val origin = shape[0]
    val band = Path()
    Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = gap.toFloat()
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
    }.getFillPath(pathOf(knife, origin, close = false), band)
    val result = pathOf(shape, origin).also { it.op(band, Path.Op.DIFFERENCE) }
    val pieces = shapesOf(result, origin, bridgeHoles = true)
    return if (pieces.size >= 2) pieces else emptyList()
}

/**
 * The open polyline [points] split where it crosses the polyline [knife]: the runs between
 * the crossings, each crossing point shared by the runs on either side of it. A single run
 * (no crossing) comes back as it is.
 */
fun splitPolyline(points: List<WorldPoint>, knife: List<WorldPoint>): List<List<WorldPoint>> {
    val runs = ArrayList<List<WorldPoint>>()
    var current = arrayListOf(points.firstOrNull() ?: return emptyList())
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        // Crossings on this segment, in order along it.
        val cuts = (1 until knife.size).mapNotNull { k -> segmentCrossing(a, b, knife[k - 1], knife[k]) }.sorted()
        for (t in cuts) {
            val at = WorldPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            current.add(at)
            runs.add(current)
            current = arrayListOf(at)
        }
        current.add(b)
    }
    runs.add(current)
    return runs.filter { it.size >= 2 }
}

/** Where segment [a]-[b] crosses segment [c]-[d], as a fraction of the way from [a] to [b]; null if they don't. */
private fun segmentCrossing(a: WorldPoint, b: WorldPoint, c: WorldPoint, d: WorldPoint): Double? {
    val rx = b.x - a.x
    val ry = b.y - a.y
    val sx = d.x - c.x
    val sy = d.y - c.y
    val denominator = rx * sy - ry * sx
    if (abs(denominator) < 1e-12) return null // parallel
    val t = ((c.x - a.x) * sy - (c.y - a.y) * sx) / denominator
    val u = ((c.x - a.x) * ry - (c.y - a.y) * rx) / denominator
    return t.takeIf { it > 0.0 && it < 1.0 && u in 0.0..1.0 }
}

/** How a contour's outline turns its corners (roadmap item 141). */
enum class ContourJoin(val paintJoin: Paint.Join) { ROUND(Paint.Join.ROUND), MITRE(Paint.Join.MITER), BEVEL(Paint.Join.BEVEL) }

/**
 * The contour [distance] (world units) around [outline]s, merged: grown outward when positive,
 * or for a closed shape shrunk inward when negative. Each entry is a point list with its
 * stroke width and whether it is a closed shape (its area counts) or a line (only its inked
 * width does). The result's loops come back as separate outlines (holes too), for drawing as
 * lines. Empty when nothing is left (an inset larger than the shape).
 */
fun contourOutlines(outlines: List<ContourSource>, distance: Double, join: ContourJoin): List<List<WorldPoint>> {
    val usable = outlines.filter { it.points.size >= 2 }
    if (usable.isEmpty() || distance == 0.0) return emptyList()
    val origin = usable[0].points[0]
    val result = Path()
    for (source in usable) {
        val path = pathOf(source.points, origin, close = source.closed)
        if (distance > 0) {
            // Grown: the outline traced at its own width plus the distance either side.
            val grown = Path()
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = (source.width + 2 * distance).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = join.paintJoin
                strokeMiter = MITRE_LIMIT
            }.getFillPath(path, grown)
            if (source.closed) grown.op(path, Path.Op.UNION)
            result.op(grown, Path.Op.UNION)
        } else if (source.closed) {
            // Shrunk: the shape less a band of the distance either side of its edge.
            val band = Path()
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = (2 * -distance).toFloat()
                strokeJoin = join.paintJoin
                strokeMiter = MITRE_LIMIT
            }.getFillPath(path, band)
            result.op(Path(path).also { it.op(band, Path.Op.DIFFERENCE) }, Path.Op.UNION)
        }
    }
    return shapesOf(result, origin, bridgeHoles = false)
}

/** An outline [contourOutlines] works from: [points], drawn [width] wide, [closed] if it is a shape. */
class ContourSource(val points: List<WorldPoint>, val width: Double, val closed: Boolean)

// Slivers left by the platform where fragments only touch, in square world units, are dropped.
private const val MIN_FRAGMENT_AREA = 0.01

// As in vector editors' default: sharper corners than this ratio are bevelled.
private const val MITRE_LIMIT = 4f

/**
 * The shape builder's two drag modes and the knife (roadmap item 141), and the comic panel
 * cutter (roadmap item 105); see [CanvasState.toggleShapeEdit].
 */
enum class ShapeEditMode { BUILD_ADD, BUILD_REMOVE, KNIFE, PANEL_CUT }

/** A new stroke styled like this one (frame constraints too), through [points] (no pressure, tilt or bone weights). */
fun Stroke.withPoints(points: List<WorldPoint>): Stroke {
    val source = this
    return Stroke(color, widthWorld, penType, filled, blendMode, layerId, animFrame, dashed, fadeFill, screentone, brush, gradient, groupId, boneId, lineStyle)
        .apply {
            for (point in points) addPoint(point)
            source.constraints?.let { setConstraints(it) }
        }
}

/**
 * A new stroke styled like this one around the closed outline [loop], its start repeated at
 * its end so an outline (not just a fill) draws closed.
 */
fun Stroke.withLoop(loop: List<WorldPoint>): Stroke = withPoints(if (loop.isNotEmpty() && loop.first() != loop.last()) loop + loop.first() else loop)

/**
 * A point inside the polygon [loop]: its centroid when that's inside (a convex region), else
 * the middle of the widest inside span of a horizontal line through the centroid's height.
 */
internal fun interiorPoint(loop: List<WorldPoint>): WorldPoint {
    val cx = loop.sumOf { it.x } / loop.size
    val cy = loop.sumOf { it.y } / loop.size
    val centroid = WorldPoint(cx, cy)
    if (polygonContains(loop, centroid)) return centroid
    val crossings = ArrayList<Double>()
    var j = loop.size - 1
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[j]
        if ((a.y > cy) != (b.y > cy)) crossings.add((b.x - a.x) * (cy - a.y) / (b.y - a.y) + a.x)
        j = i
    }
    crossings.sort()
    // Even-odd: the spans between crossings 0-1, 2-3, ... are inside.
    val widest = (0 until crossings.size / 2).maxByOrNull { crossings[2 * it + 1] - crossings[2 * it] } ?: return loop[0]
    return WorldPoint((crossings[2 * widest] + crossings[2 * widest + 1]) / 2, cy)
}
