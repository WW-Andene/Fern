package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Roadmap item 101: tracing an image's dark shapes into filled outlines. */
class ImageTraceTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    /** A 40 x 40 white image with a black 20 x 20 square ring (a 10 x 10 white hole) and a black dot. */
    private fun image(): IntArray = IntArray(40 * 40) { i ->
        val x = i % 40
        val y = i / 40
        val ring = x in 5 until 25 && y in 5 until 25 && !(x in 10 until 20 && y in 10 until 20)
        val dot = x in 30 until 34 && y in 30 until 34
        if (ring || dot) black else white
    }

    @Test
    fun aRingKeepsItsHoleAndADotIsItsOwnShape() {
        val shapes = traceImage(image(), 40, 40)
        assertEquals(2, shapes.size)
        val ring = shapes.maxBy { abs(polygonArea(it)) }
        // Filled as one polygon: ink in the ring, paper in the hole.
        assertTrue(polygonContains(ring, WorldPoint(7.0, 15.0)))
        assertTrue(!polygonContains(ring, WorldPoint(15.0, 15.0)))
        assertEquals(400.0 - 100.0, abs(polygonArea(ring)), 1.0)
        assertEquals(ring.first(), ring.last())
        val dot = shapes.minBy { abs(polygonArea(it)) }
        assertEquals(16.0, abs(polygonArea(dot)), 0.5)
    }

    @Test
    fun specksAndBlankImagesGiveNothing() {
        assertTrue(traceImage(IntArray(100) { white }, 10, 10).isEmpty())
        val speck = IntArray(100) { if (it == 55) black else white }
        assertTrue(traceImage(speck, 10, 10).isEmpty())
    }
}
