package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val square = listOf(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), WorldPoint(10.0, 10.0), WorldPoint(0.0, 10.0))

    @Test
    fun polygonContains_insideAndOutside() {
        assertTrue(polygonContains(square, WorldPoint(5.0, 5.0)))
        assertFalse(polygonContains(square, WorldPoint(15.0, 5.0)))
        assertFalse(polygonContains(square, WorldPoint(-1.0, -1.0)))
    }

    @Test
    fun polygonArea_ofSquare() {
        assertEquals(100.0, polygonArea(square), 1e-9)
        assertEquals(100.0, polygonArea(square.reversed()), 1e-9)
    }

    @Test
    fun isClosedLoop_respectsTolerance() {
        val open = square + WorldPoint(0.0, 3.0)
        assertTrue(isClosedLoop(open, 3.0))
        assertFalse(isClosedLoop(open, 2.0))
        assertFalse(isClosedLoop(listOf(WorldPoint.Zero, WorldPoint.Zero), 1.0))
    }

    @Test
    fun distanceToPolyline_projectsOntoSegment() {
        val line = listOf(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0))
        assertEquals(4.0, distanceToPolyline(line, WorldPoint(5.0, 4.0)), 1e-9)
        assertEquals(5.0, distanceToPolyline(line, WorldPoint(13.0, 4.0)), 1e-9)
        assertEquals(Double.POSITIVE_INFINITY, distanceToPolyline(emptyList(), WorldPoint.Zero), 0.0)
    }

    @Test
    fun smoothPoints_keepsCountAndEndpoints() {
        val zigzag = listOf(WorldPoint(0.0, 0.0), WorldPoint(1.0, 5.0), WorldPoint(2.0, 0.0), WorldPoint(3.0, 5.0))
        val smoothed = smoothPoints(zigzag, 2)
        assertEquals(zigzag.size, smoothed.size)
        assertEquals(zigzag.first(), smoothed.first())
        assertEquals(zigzag.last(), smoothed.last())
        assertTrue(smoothed[1].y < 5.0)
    }

    @Test
    fun resamplePolyline_spacesEvenly() {
        val line = listOf(WorldPoint(0.0, 0.0), WorldPoint(2.0, 0.0), WorldPoint(10.0, 0.0))
        val resampled = resamplePolyline(line, 6)
        assertEquals(6, resampled.size)
        resampled.forEachIndexed { i, p -> assertEquals(i * 2.0, p.x, 1e-9) }
    }
}
