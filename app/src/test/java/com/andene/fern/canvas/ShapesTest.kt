package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin

class ShapesTest {
    @Test
    fun snappedShapeEnd_snapsNearHorizontalLine() {
        val end = snappedShapeEnd(ShapeKind.LINE, WorldPoint.Zero, WorldPoint(100.0, 3.0))
        assertEquals(0.0, end.y, 1e-9)
    }

    @Test
    fun snappedShapeEnd_leavesClearAngleAlone() {
        val raw = WorldPoint(100.0, 40.0) // ~21.8°, more than 5° from 15° and 30°
        assertEquals(raw, snappedShapeEnd(ShapeKind.LINE, WorldPoint.Zero, raw))
    }

    @Test
    fun snappedShapeEnd_snapsNearSquare() {
        val end = snappedShapeEnd(ShapeKind.RECTANGLE, WorldPoint.Zero, WorldPoint(100.0, 95.0))
        assertEquals(end.x, end.y, 1e-9)
    }

    @Test
    fun generateShapePoints_rectangleIsClosed() {
        val points = generateShapePoints(ShapeKind.RECTANGLE, WorldPoint(10.0, 10.0), WorldPoint(0.0, 0.0))
        // Four corners, each doubled to stay sharp, plus the closing point.
        assertEquals(8, points.size)
        assertEquals(points.first(), points.last())
        assertEquals(WorldPoint(0.0, 0.0), points.first())
    }

    @Test
    fun recognizeShape_straightLine() {
        val wobbly = (0..20).map { WorldPoint(it * 5.0, if (it % 2 == 0) 0.5 else -0.5) }
        val clean = recognizeShape(wobbly)
        assertNotNull(clean)
        assertEquals(2, clean!!.size)
    }

    @Test
    fun recognizeShape_circle() {
        val circle = (0..40).map { val a = 2 * PI * it / 40; WorldPoint(50 * cos(a), 50 * sin(a)) }
        val clean = recognizeShape(circle)
        assertNotNull(clean)
        assertEquals(49, clean!!.size) // ellipse generator: 48 segments, closed
    }

    @Test
    fun recognizeShape_rejectsScribble() {
        // A zigzag of many short strokes has too many corners to be a polyline.
        val scribble = (0..30).map { WorldPoint(it * 4.0, if (it % 2 == 0) 0.0 else 20.0) }
        assertNull(recognizeShape(scribble))
    }

    @Test
    fun recognizeShape_vBecomesTwoStraightSegments() {
        val v = (0..20).map { WorldPoint(it * 5.0, if (it <= 10) it * 10.0 else (20 - it) * 10.0) }
        val clean = recognizeShape(v)!!
        // Start, the doubled (sharp) bend, end.
        assertEquals(listOf(WorldPoint(0.0, 0.0), WorldPoint(50.0, 100.0), WorldPoint(50.0, 100.0), WorldPoint(100.0, 0.0)), clean)
    }

    @Test
    fun recognizeShape_triangle() {
        val corners = listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), WorldPoint(50.0, 80.0), WorldPoint(0.0, 0.0))
        val outline = corners.zipWithNext().flatMap { (a, b) -> (0 until 10).map { a + (b - a) * (it / 10.0) } } + corners.last()
        val clean = recognizeShape(outline)!!
        assertEquals(clean.first(), clean.last())
        assertEquals(3, clean.distinct().size)
        assertNotNull(clean.firstOrNull { it == WorldPoint(50.0, 80.0) })
    }

    @Test
    fun recognizeShape_rotatedSquareIsAPolygonNotABox() {
        val corners = listOf(WorldPoint(50.0, 0.0), WorldPoint(100.0, 50.0), WorldPoint(50.0, 100.0), WorldPoint(0.0, 50.0), WorldPoint(50.0, 0.0))
        val outline = corners.zipWithNext().flatMap { (a, b) -> (0 until 10).map { a + (b - a) * (it / 10.0) } } + corners.last()
        assertEquals(4, recognizeShape(outline)!!.distinct().size)
    }

    @Test
    fun recognizeShape_arc() {
        val arc = (0..20).map { val a = PI * it / 20; WorldPoint(50 * cos(a), 50 * sin(a)) }
        val clean = recognizeShape(arc)!!
        assertTrue(clean.size > 4)
        assertTrue(clean.all { abs(hypot(it.x, it.y) - 50.0) < 1e-6 })
        assertEquals(50.0, clean.first().x, 1e-6)
        assertEquals(-50.0, clean.last().x, 1e-6)
        assertTrue(clean.any { it.y > 49.0 }) // passes over the top, not the other way round
    }

    @Test
    fun adjustRecognizedShape_lineEndFollowsPen() {
        val line = listOf(WorldPoint.Zero, WorldPoint(100.0, 0.0))
        val end = adjustRecognizedShape(line, WorldPoint(100.0, 0.0), WorldPoint(0.0, 200.0))[1]
        assertEquals(0.0, end.x, 1e-9)
        assertEquals(200.0, end.y, 1e-9)
    }

    @Test
    fun adjustRecognizedShape_scalesAndRotatesAboutTheCentre() {
        val square = listOf(WorldPoint(-1.0, -1.0), WorldPoint(1.0, -1.0), WorldPoint(1.0, 1.0), WorldPoint(-1.0, 1.0))
        // The pen moves from (1, 1) to (-2, 2): twice as far, a quarter turn.
        val moved = adjustRecognizedShape(square, WorldPoint(1.0, 1.0), WorldPoint(-2.0, 2.0))
        assertEquals(-2.0, moved[2].x, 1e-9)
        assertEquals(2.0, moved[2].y, 1e-9)
    }
}
