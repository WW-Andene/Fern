package com.andene.fern.canvas

import androidx.annotation.StringRes
import com.andene.fern.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Guides (roadmap items 340-343): drawing aids placed on the page, the way Clip Studio's rulers,
 * Procreate's drawing guides and Krita's assistants work. A guide is placed, then locked; while
 * locked it never takes a touch, and any drawing tool snaps to it. Editing a guide is an explicit
 * mode. Several guides can be on the page at once: each stroke follows the one that best matches
 * how it starts.
 */

/** The kinds of guide. Each defines the lines a stroke may follow (see [Guide.constraint]). */
enum class GuideKind(@StringRes val label: Int, val handleCount: Int) {
    /** A straight edge between two handles: strokes starting near it run along it. */
    STRAIGHT(R.string.guide_straight, 2),
    /** A direction (two handles): every stroke runs parallel to it, wherever it starts. */
    PARALLEL(R.string.guide_parallel, 2),
    /** A centre: every stroke runs straight towards or away from it (speed and focus lines). */
    RADIAL(R.string.guide_radial, 1),
    /** A centre: every stroke follows a circle around it. */
    CONCENTRIC(R.string.guide_concentric, 1),
    /** An ellipse (centre, end of the first axis, end of the second): strokes starting near it follow it. */
    ELLIPSE(R.string.guide_ellipse, 3),
}

/** One guide on the page: its kind and its handles in world space. */
data class Guide(
    val id: String,
    val kind: GuideKind,
    val handles: List<WorldPoint>,
    val visible: Boolean = true,
) {
    fun shiftedBy(offset: WorldPoint) = copy(handles = handles.map { it - offset })

    /**
     * The constraint this guide puts on a stroke that started at [start], or null when the guide
     * doesn't apply to it: straight edges and ellipses only catch strokes starting within [reach]
     * (world units) of them, the other kinds catch every stroke.
     */
    fun constraint(start: WorldPoint, reach: Double): ((WorldPoint) -> WorldPoint)? {
        if (!visible) return null
        return when (kind) {
            GuideKind.STRAIGHT -> {
                val (a, b) = handles
                val dir = WorldPoint(b.x - a.x, b.y - a.y)
                if (hypot(dir.x, dir.y) < 1e-12) return null
                val onLine = projectOntoLine(a, dir, start)
                if (hypot(onLine.x - start.x, onLine.y - start.y) > reach) null else { p -> projectOntoLine(a, dir, p) }
            }
            GuideKind.PARALLEL -> {
                val (a, b) = handles
                val dir = WorldPoint(b.x - a.x, b.y - a.y)
                if (hypot(dir.x, dir.y) < 1e-12) null else { p -> projectOntoLine(start, dir, p) }
            }
            GuideKind.RADIAL -> {
                val c = handles[0]
                val dir = WorldPoint(start.x - c.x, start.y - c.y)
                if (hypot(dir.x, dir.y) < 1e-12) null else { p -> projectOntoLine(c, dir, p) }
            }
            GuideKind.CONCENTRIC -> {
                val c = handles[0]
                val r = hypot(start.x - c.x, start.y - c.y)
                if (r < 1e-12) null else { p -> onCircle(c, r, p) }
            }
            GuideKind.ELLIPSE -> {
                val e = ellipseOf(handles)
                if (e.rx < 1e-12 || e.ry < 1e-12) return null
                val near = e.nearestTo(start)
                if (hypot(near.x - start.x, near.y - start.y) > reach) null else { p -> e.nearestTo(p) }
            }
        }
    }
}

/** A starting set of handles for a new guide of [kind], centred on [center] and [size] across (world units). */
fun newGuideHandles(kind: GuideKind, center: WorldPoint, size: Double): List<WorldPoint> = when (kind) {
    GuideKind.STRAIGHT, GuideKind.PARALLEL -> listOf(WorldPoint(center.x - size / 2, center.y), WorldPoint(center.x + size / 2, center.y))
    GuideKind.RADIAL, GuideKind.CONCENTRIC -> listOf(center)
    GuideKind.ELLIPSE -> listOf(center, WorldPoint(center.x + size / 2, center.y), WorldPoint(center.x, center.y - size / 4))
}

private fun onCircle(c: WorldPoint, r: Double, p: WorldPoint): WorldPoint {
    val angle = atan2(p.y - c.y, p.x - c.x)
    return WorldPoint(c.x + r * cos(angle), c.y + r * sin(angle))
}

/** An ellipse with centre, radii and the angle of its first axis. */
class GuideEllipse(val center: WorldPoint, val rx: Double, val ry: Double, val angle: Double) {
    /** The point of the ellipse at parameter [t] (radians). */
    fun at(t: Double): WorldPoint {
        val x = rx * cos(t)
        val y = ry * sin(t)
        return WorldPoint(center.x + x * cos(angle) - y * sin(angle), center.y + x * sin(angle) + y * cos(angle))
    }

    /**
     * The point of the ellipse nearest [p], to within a fraction of a degree: the parameter is
     * found by projecting [p] into the ellipse's frame, then refined by a few Newton steps.
     */
    fun nearestTo(p: WorldPoint): WorldPoint {
        val dx = p.x - center.x
        val dy = p.y - center.y
        val lx = dx * cos(-angle) - dy * sin(-angle)
        val ly = dx * sin(-angle) + dy * cos(-angle)
        var t = atan2(ly * rx, lx * ry)
        repeat(ELLIPSE_NEWTON_STEPS) {
            val ex = rx * cos(t)
            val ey = ry * sin(t)
            // d/dt of half the squared distance, and its derivative.
            val f = (rx * rx - ry * ry) * sin(t) * cos(t) - lx * rx * sin(t) + ly * ry * cos(t)
            val df = (rx * rx - ry * ry) * (cos(t) * cos(t) - sin(t) * sin(t)) - lx * ex - ly * ey
            if (kotlin.math.abs(df) > 1e-12) t -= f / df
        }
        return at(t)
    }
}

/** The ellipse of an ELLIPSE guide's handles: centre, end of the first axis, a point setting the second radius. */
fun ellipseOf(handles: List<WorldPoint>): GuideEllipse {
    val (c, a, b) = handles
    val angle = atan2(a.y - c.y, a.x - c.x)
    val rx = hypot(a.x - c.x, a.y - c.y)
    // The second radius is the third handle's distance from the first axis.
    val ry = kotlin.math.abs(-(b.x - c.x) * sin(angle) + (b.y - c.y) * cos(angle))
    return GuideEllipse(c, rx, ry, angle)
}

private const val ELLIPSE_NEWTON_STEPS = 4
