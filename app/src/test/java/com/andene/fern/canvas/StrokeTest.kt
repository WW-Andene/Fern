package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeTest {
    private fun line(vararg xs: Double) = Stroke(Color.Black, 2.0).apply { xs.forEach { addPoint(WorldPoint(it, 0.0)) } }

    @Test
    fun boundsFollowPoints() {
        val stroke = line(-5.0, 3.0, 8.0)
        assertEquals(-5.0, stroke.minX, 0.0)
        assertEquals(8.0, stroke.maxX, 0.0)
        assertTrue(stroke.intersects(7.0, -1.0, 20.0, 1.0))
        assertFalse(stroke.intersects(20.0, -1.0, 30.0, 1.0))
    }

    @Test
    fun shiftByMovesPointsAndBounds() {
        val stroke = line(0.0, 10.0)
        stroke.shiftBy(WorldPoint(4.0, 1.0))
        assertEquals(WorldPoint(-4.0, -1.0), stroke.points.first())
        assertEquals(6.0, stroke.maxX, 0.0)
        assertEquals(-1.0, stroke.minY, 0.0)
    }

    @Test
    fun eraseNear_splitsIntoPieces() {
        val stroke = line(0.0, 1.0, 2.0, 5.0, 8.0, 9.0, 10.0)
        val pieces = stroke.eraseNear(WorldPoint(5.0, 0.0), 1.5)!!
        assertEquals(2, pieces.size)
        assertEquals(listOf(0.0, 1.0, 2.0), pieces[0].points.map { it.x })
        assertEquals(listOf(8.0, 9.0, 10.0), pieces[1].points.map { it.x })
    }

    @Test
    fun eraseNear_missReturnsNull() {
        assertNull(line(0.0, 1.0).eraseNear(WorldPoint(50.0, 50.0), 1.0))
    }

    @Test
    fun deepCopyIsIndependent() {
        val stroke = line(0.0, 1.0)
        val copy = stroke.deepCopy()
        stroke.shiftBy(WorldPoint(1.0, 0.0))
        assertEquals(0.0, copy.points.first().x, 0.0)
    }

    @Test
    fun growsPastInitialCapacityKeepingStylusData() {
        val stroke = Stroke(Color.Black, 1.0)
        repeat(1000) { stroke.addPoint(WorldPoint(it.toDouble(), -it.toDouble()), it / 1000f, 0.5f, 0.25f) }
        assertEquals(1000, stroke.points.size)
        assertEquals(WorldPoint(999.0, -999.0), stroke.points[999])
        assertEquals(0.999f, stroke.pressureAt(999), 0f)
        assertEquals(0.5f, stroke.tilts[500], 0f)
        assertEquals(0.25f, stroke.orientationAt(0), 0f)
    }

    @Test
    fun viewsAreLive() {
        val stroke = line(0.0)
        val points = stroke.points
        stroke.addPoint(WorldPoint(1.0, 0.0))
        assertEquals(2, points.size)
    }

    @Test
    fun setPointsAcceptsItsOwnView() {
        val stroke = line(0.0, 1.0, 2.0)
        stroke.setPoints(stroke.points)
        assertEquals(listOf(0.0, 1.0, 2.0), stroke.points.map { it.x })
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPointsRejectsADifferentCount() {
        line(0.0, 1.0).setPoints(listOf(WorldPoint.Zero))
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun readingPastTheEndFails() {
        line(0.0).pressureAt(1)
    }
}
