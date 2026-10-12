package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
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
        assertEquals(5, points.size)
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
        val vShape = listOf(WorldPoint(0.0, 0.0), WorldPoint(50.0, 100.0), WorldPoint(100.0, 0.0))
        assertNull(recognizeShape(vShape))
    }
}
