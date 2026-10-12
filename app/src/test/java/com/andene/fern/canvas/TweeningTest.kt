package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Roadmap item 82: shape tweening pairs strokes by position and lines up closed outlines. */
class TweeningTest {
    private fun line(x: Double, frame: Int = 0) = Stroke(Color.Black, 2.0, animFrame = frame).apply {
        addPoint(WorldPoint(x, 0.0)); addPoint(WorldPoint(x, 10.0))
    }

    @Test
    fun strokesPairWithTheNearestCounterpartWhateverTheDrawingOrder() {
        val left = line(0.0)
        val right = line(100.0)
        val rightMoved = line(105.0, 1)
        val leftMoved = line(5.0, 1)
        // The next frame was drawn right first.
        val pairs = tweenPairs(listOf(left, right), listOf(rightMoved, leftMoved))
        assertEquals(listOf(left to leftMoved, right to rightMoved), pairs)
    }

    private fun circle(startAngle: Double, reversed: Boolean) = Stroke(Color.Black, 2.0).apply {
        for (i in 0..36) {
            val a = startAngle + (if (reversed) -1 else 1) * 2 * PI * i / 36
            addPoint(WorldPoint(50 * cos(a), 50 * sin(a)))
        }
    }

    @Test
    fun closedOutlinesAreLinedUpSoTheTweenDoesNotTwist() {
        // Same circle, drawn from the opposite side and the other way round.
        val (pa, pb) = tweenSamples(circle(0.0, false), circle(PI, true), 37)
        val worst = pa.indices.maxOf { hypot(pa[it].x - pb[it].x, pa[it].y - pb[it].y) }
        assertTrue("worst point gap $worst", worst < 5.0) // a radius of 50; unaligned it would be 100
        assertEquals(pb.first(), pb.last())
    }
}
