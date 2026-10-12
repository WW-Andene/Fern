package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class StampBrushTest {
    private val line = listOf(Offset(0f, 0f), Offset(30f, 0f), Offset(100f, 0f))

    @Test
    fun stampsAreEvenlySpacedAcrossPoints() {
        val stamps = stampPlacements(line, widthScreen = 10f, brush = StampBrush(spacing = 0.5f))
        assertEquals(21, stamps.size) // every 5 px from 0 to 100
        stamps.forEachIndexed { i, stamp -> assertEquals(i * 5f, stamp.center.x, 1e-3f) }
    }

    @Test
    fun noJitterMeansUniformStamps() {
        val stamps = stampPlacements(line, 10f, StampBrush())
        assertTrue(stamps.all { it.size == 10f && it.angle == 0f && it.center.y == 0f })
    }

    @Test
    fun scatterStaysWithinBoundsAndIsDeterministic() {
        val brush = StampBrush(scatter = 1f, sizeJitter = 0.5f, angleJitter = 1f)
        val a = stampPlacements(line, 10f, brush)
        val b = stampPlacements(line, 10f, brush)
        assertEquals(a, b) // same every frame: no shimmer
        assertTrue(a.all { abs(it.center.y) <= 10f + 1e-3f })
        assertTrue(a.all { it.size in 5f..10f })
        assertTrue(a.any { it.center.y != 0f })
    }

    @Test
    fun followDirectionRotatesWithThePath() {
        val vertical = listOf(Offset(0f, 0f), Offset(0f, 50f))
        val stamps = stampPlacements(vertical, 10f, StampBrush(followDirection = true))
        assertTrue(stamps.all { abs(it.angle - (Math.PI / 2).toFloat()) < 1e-4f })
    }

    @Test
    fun singlePointGivesOneStamp() {
        assertEquals(1, stampPlacements(listOf(Offset(3f, 4f)), 10f, StampBrush()).size)
    }

    @Test
    fun clampedPullsValuesIntoRange() {
        val brush = StampBrush(spacing = 0f, scatter = 9f, sizeJitter = -1f, angleJitter = 2f).clamped()
        assertEquals(StampBrush.MIN_SPACING, brush.spacing)
        assertEquals(StampBrush.MAX_SCATTER, brush.scatter)
        assertEquals(0f, brush.sizeJitter)
        assertEquals(1f, brush.angleJitter)
    }

    @Test
    fun starHasAlternatingRadii() {
        val star = starOutline(20f, 0f)
        assertEquals(10, star.size)
        assertEquals(10f, hypot(star[0].x, star[0].y), 1e-4f)
        assertEquals(4f, hypot(star[1].x, star[1].y), 1e-4f)
        assertEquals(-10f, star[0].y, 1e-4f) // first point straight up
    }
}
