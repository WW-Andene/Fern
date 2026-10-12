package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Comic-making tools (roadmap item 105), after Clip Studio Paint's comic features: thought and
 * shout balloons, page layouts, and speed and focus lines.
 */

/** A thought balloon's cloud outline filling the box [start]-[end]: scallops around an ellipse, closed. */
fun thoughtCloudPoints(start: WorldPoint, end: WorldPoint): List<WorldPoint> {
    val cx = (start.x + end.x) / 2
    val cy = (start.y + end.y) / 2
    val rx = abs(end.x - start.x) / 2
    val ry = abs(end.y - start.y) / 2
    // Each scallop bulges outward from the ellipse between two points on it.
    val points = ArrayList<WorldPoint>()
    for (lobe in 0 until CLOUD_LOBES) {
        val a0 = 2 * PI * lobe / CLOUD_LOBES
        val a1 = 2 * PI * (lobe + 1) / CLOUD_LOBES
        for (k in 0..CLOUD_LOBE_SEGMENTS) {
            if (lobe > 0 && k == 0) continue
            val t = k.toDouble() / CLOUD_LOBE_SEGMENTS
            val angle = a0 + (a1 - a0) * t
            val bulge = 1 + CLOUD_BULGE * sin(PI * t)
            points.add(WorldPoint(cx + rx * bulge * cos(angle), cy + ry * bulge * sin(angle)))
        }
    }
    return points
}

/** The small circles trailing from a thought balloon toward the speaker (lower left), largest first. */
fun thoughtTrail(start: WorldPoint, end: WorldPoint): List<List<WorldPoint>> {
    val cx = (start.x + end.x) / 2
    val cy = (start.y + end.y) / 2
    val rx = abs(end.x - start.x) / 2
    val ry = abs(end.y - start.y) / 2
    val unit = min(rx, ry)
    return listOf(0.16 to 1.25, 0.1 to 1.55, 0.06 to 1.8).map { (radius, distance) ->
        val centre = WorldPoint(cx - rx * distance * 0.7, cy + ry * distance)
        (0..TRAIL_SEGMENTS).map { i ->
            val angle = 2 * PI * i / TRAIL_SEGMENTS
            WorldPoint(centre.x + unit * radius * cos(angle), centre.y + unit * radius * sin(angle))
        }
    }
}

/** A shout balloon: a spiky burst around the ellipse filling [start]-[end], closed, with spikes of varied length. */
fun shoutBurstPoints(start: WorldPoint, end: WorldPoint): List<WorldPoint> {
    val cx = (start.x + end.x) / 2
    val cy = (start.y + end.y) / 2
    val rx = abs(end.x - start.x) / 2
    val ry = abs(end.y - start.y) / 2
    val points = ArrayList<WorldPoint>()
    for (i in 0..BURST_SPIKES * 2) {
        val angle = PI * i / BURST_SPIKES
        // Outer points alternate between two lengths so the burst looks hand-drawn, not like a star.
        val reach = when {
            i % 2 == 1 -> BURST_INNER
            (i / 2) % 2 == 0 -> 1.0
            else -> BURST_SHORT
        }
        points.add(WorldPoint(cx + rx * reach * cos(angle), cy + ry * reach * sin(angle)))
    }
    // Sharp corners at every point: doubled, as [withSharpCorners] does for the other shapes.
    return points.flatMapIndexed { i, p -> if (i == 0 || i == points.lastIndex) listOf(p) else listOf(p, p) }
}

/** Page sizes for a comic page (roadmap item 105), as width:height. */
enum class ComicPageSize(val width: Double, val height: Double) {
    /** The classic 2:3 page. */
    CLASSIC(2.0, 3.0),
    /** JIS B5 (182 x 257 mm), the usual printed manga page. */
    MANGA(182.0, 257.0),
    /** ISO A4. */
    A4(210.0, 297.0),
    /** US comic book trim, 6.625 x 10.25 in. */
    US_COMIC(6.625, 10.25),
    /** A square page (webcomics, social media). */
    SQUARE(1.0, 1.0),
    /** A tall webtoon strip, 1:3. */
    WEBTOON(1.0, 3.0),
}

/** Panel layouts: each row's panel widths as fractions of the row (rows share the height equally). */
enum class ComicLayout(val rows: List<List<Double>>) {
    SINGLE(listOf(listOf(1.0))),
    TWO_ROWS(listOf(listOf(1.0), listOf(1.0))),
    THREE_ROWS(listOf(listOf(1.0), listOf(1.0), listOf(1.0))),
    /** Four stacked panels: the Japanese 4-koma strip. */
    FOUR_KOMA(listOf(listOf(1.0), listOf(1.0), listOf(1.0), listOf(1.0))),
    /** The classic five: two, one wide, two. */
    CLASSIC_FIVE(listOf(listOf(0.5, 0.5), listOf(1.0), listOf(0.5, 0.5))),
    /** The six-panel grid. */
    GRID_SIX(listOf(listOf(0.5, 0.5), listOf(0.5, 0.5), listOf(0.5, 0.5))),
    /** Nine panels, three by three. */
    GRID_NINE(listOf(listOf(1.0 / 3, 1.0 / 3, 1.0 / 3), listOf(1.0 / 3, 1.0 / 3, 1.0 / 3), listOf(1.0 / 3, 1.0 / 3, 1.0 / 3))),
}

/**
 * The panel rectangles (left, top, right, bottom) of [layout] on a page from [left], [top] of
 * [width] x [height], inside [margin], [gutter] apart.
 */
fun comicPanels(layout: ComicLayout, left: Double, top: Double, width: Double, height: Double, margin: Double, gutter: Double): List<DoubleArray> {
    val rows = layout.rows
    val innerWidth = width - 2 * margin
    val rowHeight = (height - 2 * margin - gutter * (rows.size - 1)) / rows.size
    return rows.flatMapIndexed { r, fractions ->
        val usable = innerWidth - gutter * (fractions.size - 1)
        var x = left + margin
        val y = top + margin + r * (rowHeight + gutter)
        fractions.map { fraction ->
            val w = usable * fraction
            doubleArrayOf(x, y, x + w, y + rowHeight).also { x += w + gutter }
        }
    }
}

/** Speed lines run parallel across a region; focus lines converge on its centre. */
enum class ComicLineKind { SPEED, FOCUS }

/**
 * Speed or focus lines (roadmap item 105) over the box [min]-[max], [count] of them, each a
 * two-point line from its outer end to its inner end (strokes taper toward the inner end).
 * Focus lines start on the box's edge and stop at a ragged ellipse whose size is [inner] (0-1)
 * of the box; speed lines cross the box at [angleDegrees], their lengths between [inner] and
 * the full width. [seed] makes the raggedness repeatable.
 */
fun comicLines(kind: ComicLineKind, min: WorldPoint, max: WorldPoint, count: Int, inner: Double, angleDegrees: Double, seed: Int): List<List<WorldPoint>> {
    val random = Random(seed)
    val cx = (min.x + max.x) / 2
    val cy = (min.y + max.y) / 2
    val hw = (max.x - min.x) / 2
    val hh = (max.y - min.y) / 2
    if (hw <= 0 || hh <= 0 || count <= 0) return emptyList()
    return when (kind) {
        ComicLineKind.FOCUS -> (0 until count).map { i ->
            val angle = 2 * PI * (i + random.nextDouble() * 0.6) / count
            val dx = cos(angle)
            val dy = sin(angle)
            // Out to the box's edge along this direction.
            val reach = min(if (dx != 0.0) hw / abs(dx) else Double.MAX_VALUE, if (dy != 0.0) hh / abs(dy) else Double.MAX_VALUE)
            val stop = inner * (1 + (random.nextDouble() - 0.5) * FOCUS_RAGGEDNESS)
            listOf(WorldPoint(cx + dx * reach, cy + dy * reach), WorldPoint(cx + dx * hw * stop, cy + dy * hh * stop))
        }
        ComicLineKind.SPEED -> {
            val angle = Math.toRadians(angleDegrees)
            val ux = cos(angle)
            val uy = sin(angle)
            // Lines spread across the box's extent perpendicular to their direction.
            val across = abs(-uy * hw) + abs(ux * hh)
            val along = abs(ux * hw) + abs(uy * hh)
            (0 until count).map { i ->
                val offset = -across + 2 * across * (i + random.nextDouble()) / count
                val length = along * 2 * (inner + (1 - inner) * random.nextDouble())
                // Each line ends at the box's trailing edge and reaches back by its length.
                val endAlong = along * (1 - random.nextDouble() * 0.2)
                val baseX = cx - uy * offset
                val baseY = cy + ux * offset
                val tail = WorldPoint(baseX - ux * endAlong, baseY - uy * endAlong)
                val head = WorldPoint(tail.x + ux * length, tail.y + uy * length)
                listOf(clampTo(tail, min, max), clampTo(head, min, max))
            }.filter { (a, b) -> abs(a.x - b.x) + abs(a.y - b.y) > 0 }
        }
    }
}

private fun clampTo(p: WorldPoint, min: WorldPoint, max: WorldPoint) = WorldPoint(p.x.coerceIn(min.x, max.x), p.y.coerceIn(min.y, max.y))

/** The default gutter between comic panels, as a fraction of the page's width. */
const val COMIC_GUTTER_FRACTION = 0.03

/** The default page margin around the panels, as a fraction of the page's width. */
const val COMIC_MARGIN_FRACTION = 0.06

private const val CLOUD_LOBES = 10
private const val CLOUD_LOBE_SEGMENTS = 8
private const val CLOUD_BULGE = 0.14
private const val TRAIL_SEGMENTS = 24
private const val BURST_SPIKES = 14
private const val BURST_INNER = 0.78
private const val BURST_SHORT = 0.9
private const val FOCUS_RAGGEDNESS = 0.4
