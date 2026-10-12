package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Roadmap item 94: the gap-closing fill. */
class GapFillTest {
    private fun line(x0: Double, y0: Double, x1: Double, y1: Double) = Stroke(Color.Black, 2.0).apply {
        addPoint(WorldPoint(x0, y0)); addPoint(WorldPoint(x1, y1))
    }

    /** A 100 x 100 square of four separate lines, each corner left open by [gap]. */
    private fun leakySquare(gap: Double) = listOf(
        line(0.0 + gap, 0.0, 100.0 - gap, 0.0), line(100.0, 0.0 + gap, 100.0, 100.0 - gap),
        line(100.0 - gap, 100.0, 0.0 + gap, 100.0), line(0.0, 100.0 - gap, 0.0, 0.0 + gap),
    )

    private fun fill(walls: List<Stroke>, seed: WorldPoint) =
        gapClosingFill(walls, seed, -100.0, -100.0, 200.0, 200.0, cell = 1.0, gapWorld = 4.0)

    @Test
    fun smallGapsAreClosedAndTheFillReachesUnderTheLines() {
        val outline = requireNotNull(fill(leakySquare(gap = 2.0), WorldPoint(50.0, 50.0)))
        val area = abs(polygonArea(outline))
        assertTrue("area $area", area in 9200.0..10400.0) // about the 100 x 100 square, to the lines' middles
        assertTrue(polygonContains(outline, WorldPoint(2.0, 50.0)))
        assertTrue(!polygonContains(outline, WorldPoint(-6.0, 50.0)))
        assertEquals(outline.first(), outline.last())
        assertTrue("simplified to ${outline.size} points", outline.size < 200)
    }

    @Test
    fun wideGapsLeakSoNothingIsFilled() {
        assertNull(fill(leakySquare(gap = 20.0), WorldPoint(50.0, 50.0)))
    }

    @Test
    fun outsideTheShapeIsOpenSpace() {
        assertNull(fill(leakySquare(gap = 2.0), WorldPoint(150.0, 150.0)))
    }
}
