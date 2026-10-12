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
}
