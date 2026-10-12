package com.andene.fern.canvas

import android.graphics.Path
import kotlin.math.abs
import kotlin.math.hypot

/** Boolean operations on closed shapes (roadmap item 96), as in vector editors' pathfinder panels. */
enum class ShapeOp(val pathOp: Path.Op) {
    /** Everything any shape covers. */
    UNION(Path.Op.UNION),
    /** Only what every shape covers. */
    INTERSECT(Path.Op.INTERSECT),
    /** The bottom shape with the others cut out of it. */
    SUBTRACT(Path.Op.DIFFERENCE),
    /** What an odd number of shapes covers (overlaps cut away). */
    EXCLUDE(Path.Op.XOR),
}

// Curve flattening tolerance for the result, in the local units below (world units).
private const val BOOLEAN_APPROXIMATION_ERROR = 0.25f

/**
 * [op] applied to the closed outlines [shapes] (bottom first), with the platform's path
 * operations. The result's outlines come back as point loops in world units, outer outlines
 * first. With [bridgeHoles], each hole is joined into the outline around it by a zero-width
 * cut, so a single filled polygon shows it as empty (its winding is opposite to the outline's,
 * and fills use the non-zero rule); otherwise holes are returned as loops of their own, which is
 * what an outlined (unfilled) result needs. Empty when nothing is left.
 *
 * Works relative to the shapes' first point: the platform paths are single precision, and
 * world coordinates far from the origin would lose detail.
 */
fun combineShapes(shapes: List<List<WorldPoint>>, op: ShapeOp, bridgeHoles: Boolean): List<List<WorldPoint>> {
    val nonEmpty = shapes.filter { it.size >= 3 }
    if (nonEmpty.size < 2) return emptyList()
    val origin = nonEmpty[0][0]
    val result = pathOf(nonEmpty[0], origin)
    for (shape in nonEmpty.drop(1)) result.op(pathOf(shape, origin), op.pathOp)
    return shapesOf(result, origin, bridgeHoles)
}

/** [points] as a closed platform path, relative to [origin] (see [combineShapes] on precision). */
internal fun pathOf(points: List<WorldPoint>, origin: WorldPoint, close: Boolean = true) = Path().apply {
    moveTo((points[0].x - origin.x).toFloat(), (points[0].y - origin.y).toFloat())
    for (i in 1 until points.size) lineTo((points[i].x - origin.x).toFloat(), (points[i].y - origin.y).toFloat())
    if (close) close()
}

/**
 * The outlines of [path] (relative to [origin]) as world point loops, outer outlines first;
 * holes bridged into their outline or returned as loops of their own, as in [combineShapes].
 */
internal fun shapesOf(path: Path, origin: WorldPoint, bridgeHoles: Boolean): List<List<WorldPoint>> {
    val loops = contoursOf(path).map { loop -> loop.map { WorldPoint(it.first + origin.x, it.second + origin.y) } }.filter { it.size >= 3 }
    if (loops.isEmpty()) return emptyList()
    // A loop inside another is a hole of it (the platform returns holes with the opposite winding).
    val outers = loops.filter { loop -> loops.none { other -> other !== loop && abs(polygonArea(other)) > abs(polygonArea(loop)) && polygonContains(other, loop[0]) } }
    val holes = loops.filter { it !in outers }
    if (!bridgeHoles) return outers + holes
    return outers.map { outer ->
        // A hole must wind against its outline for the non-zero fill to leave it empty; the
        // platform usually returns it that way, but not always (an even-odd path's holes).
        val outerSign = signedArea(outer) > 0
        holes.filter { polygonContains(outer, it[0]) }
            .map { hole -> if ((signedArea(hole) > 0) == outerSign) hole.reversed() else hole }
            .fold(outer) { joined, hole -> bridge(joined, hole) }
    }
}

/** Twice the signed area of the polygon [points] (shoelace): its sign tells the winding direction. */
private fun signedArea(points: List<WorldPoint>): Double {
    var sum = 0.0
    var j = points.size - 1
    for (i in points.indices) {
        sum += (points[j].x - points[i].x) * (points[j].y + points[i].y)
        j = i
    }
    return sum
}

/** The point loops of [path], flattened ([Path.approximate]); a new loop starts where the fraction repeats. */
private fun contoursOf(path: Path): List<List<Pair<Double, Double>>> {
    val data = path.approximate(BOOLEAN_APPROXIMATION_ERROR)
    val loops = ArrayList<ArrayList<Pair<Double, Double>>>()
    var current = ArrayList<Pair<Double, Double>>()
    var lastFraction = -1f
    var i = 0
    while (i + 2 < data.size) {
        val fraction = data[i]
        val point = data[i + 1].toDouble() to data[i + 2].toDouble()
        // Each contour's start shares its fraction with the previous contour's end.
        if (fraction == lastFraction && current.isNotEmpty()) {
            loops.add(current)
            current = ArrayList()
        }
        current.add(point)
        lastFraction = fraction
        i += 3
    }
    if (current.isNotEmpty()) loops.add(current)
    // approximate() repeats each closed contour's start at its end; drop the repeat.
    return loops.map { loop -> if (loop.size > 1 && loop.first() == loop.last()) loop.dropLast(1) else loop }
}

/** [outer] with [hole] spliced in at their closest pair of points, there and back along one cut. */
private fun bridge(outer: List<WorldPoint>, hole: List<WorldPoint>): List<WorldPoint> {
    var bestI = 0
    var bestJ = 0
    var best = Double.MAX_VALUE
    for (i in outer.indices) for (j in hole.indices) {
        val d = hypot(outer[i].x - hole[j].x, outer[i].y - hole[j].y)
        if (d < best) {
            best = d
            bestI = i
            bestJ = j
        }
    }
    val around = hole.subList(bestJ, hole.size) + hole.subList(0, bestJ) + hole[bestJ]
    return outer.subList(0, bestI + 1) + around + outer.subList(bestI, outer.size)
}
