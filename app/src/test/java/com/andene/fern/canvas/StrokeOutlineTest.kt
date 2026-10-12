package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StrokeOutlineTest {
    private fun polygonArea(points: List<Offset>): Double = polygonArea(points.map { WorldPoint(it.x.toDouble(), it.y.toDouble()) })

    @Test
    fun straightConstantWidthSquareCapsIsARectangle() {
        val outline = variableWidthOutline(listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(20f, 0f)), floatArrayOf(2f, 2f, 2f), OutlineCap.SQUARE)
        // Square caps extend half a width past each end: 24 long, 4 wide.
        assertEquals(24.0 * 4.0, polygonArea(outline), 1e-3)
        assertEquals(-2f, outline.minOf { it.x }, 1e-4f)
        assertEquals(22f, outline.maxOf { it.x }, 1e-4f)
        assertEquals(2f, outline.maxOf { abs(it.y) }, 1e-4f)
    }

    @Test
    fun roundCapsAddApproximatelyADisc() {
        val outline = variableWidthOutline(listOf(Offset(0f, 0f), Offset(20f, 0f)), floatArrayOf(2f, 2f), OutlineCap.ROUND)
        // Body 20 x 4 plus two half-discs of radius 2 (an 8-segment polygon slightly under pi*r^2).
        val area = polygonArea(outline)
        assertTrue(area > 80.0 + 0.9 * Math.PI * 4 && area < 80.0 + Math.PI * 4)
    }

    @Test
    fun widthFollowsPerPointHalfWidths() {
        val outline = variableWidthOutline(listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(20f, 0f)), floatArrayOf(1f, 4f, 1f), OutlineCap.SQUARE)
        assertTrue(outline.any { abs(it.x - 10f) < 1e-4f && abs(it.y - 4f) < 1e-4f })
        assertTrue(outline.any { abs(it.x - 10f) < 1e-4f && abs(it.y + 4f) < 1e-4f })
    }

    @Test
    fun repeatedPointsDoNotBreakTheOutline() {
        val outline = variableWidthOutline(
            listOf(Offset(0f, 0f), Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 0f)),
            floatArrayOf(2f, 2f, 2f, 2f),
            OutlineCap.SQUARE,
        )
        assertTrue(outline.all { it.x.isFinite() && it.y.isFinite() })
        assertEquals(14.0 * 4.0, polygonArea(outline), 1e-3)
    }

    @Test
    fun allPointsCoincidingGivesNoOutline() {
        assertTrue(variableWidthOutline(listOf(Offset(5f, 5f), Offset(5f, 5f)), floatArrayOf(1f, 1f), OutlineCap.ROUND).isEmpty())
    }
}
