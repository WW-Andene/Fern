package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 81: easing of generated in-betweens and motion paths. */
class EasingTest {
    @Test
    fun curvesKeepTheKeysAndOrderTheirMiddles() {
        for (e in Easing.entries) {
            assertEquals(0.0, e.at(0.0), 1e-12)
            assertEquals(1.0, e.at(1.0), 1e-12)
        }
        assertTrue(Easing.EASE_IN.at(0.25) < 0.25 && Easing.EASE_OUT.at(0.25) > 0.25)
        assertEquals(0.5, Easing.EASE_IN_OUT.at(0.5), 1e-12)
    }

    private fun line(frame: Int, y: Double) = Stroke(Color.Black, 2.0, animFrame = frame).apply {
        addPoint(WorldPoint(0.0, y)); addPoint(WorldPoint(10.0, y))
    }

    @Test
    fun easedInBetweensSpaceTheirPositions() {
        val state = CanvasState()
        state.loadStrokes(listOf(line(0, 0.0), line(1, 100.0)))
        state.motionEasing = Easing.EASE_IN
        state.generateInBetweens(3)
        val ys = (1..3).map { f -> state.strokes.single { it.animFrame == f }.points.first().y }
        // Times 1/4, 2/4, 3/4 eased by t²: 6.25, 25, 56.25.
        assertEquals(listOf(6.25, 25.0, 56.25).map { "%.4f".format(it) }, ys.map { "%.4f".format(it) })
    }

    @Test
    fun linearMotionPathStaysEven() {
        val state = CanvasState()
        val mover = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)) }
        val path = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(110.0, 0.0)) }
        state.loadStrokes(listOf(mover, path))
        state.selectAllOnActiveLayer()
        state.animateAlongPath(steps = 12)
        val xs = (1 until 12).map { f -> state.strokes.single { it.animFrame == f }.points.first().x }
        for ((i, x) in xs.withIndex()) assertEquals(10.0 * (i + 1), x, 1e-6)
    }

    @Test
    fun aCubicCurveMatchesKnownPoints() {
        val linear = CubicEasing(0.25, 0.25, 0.75, 0.75)
        assertEquals(0.3, linear.at(0.3), 1e-9)
        // Symmetric ease-in-out: halfway in time is halfway in progress, slower near the keys.
        assertEquals(0.5, CubicEasing.DEFAULT.at(0.5), 1e-9)
        assertTrue(CubicEasing.DEFAULT.at(0.1) < 0.1)
        // A handle above 1 overshoots before settling.
        val overshoot = CubicEasing(0.3, 1.6, 0.6, 1.2)
        assertTrue((1..99).any { overshoot.at(it / 100.0) > 1.0 })
        assertEquals(1.0, overshoot.at(1.0), 1e-12)
    }

    @Test
    fun theCustomEasingFollowsTheStatesCurve() {
        val state = CanvasState()
        state.motionEasing = Easing.CUSTOM
        state.motionCurve = CubicEasing(0.0, 0.0, 0.0, 1.0) // fast out
        assertTrue(state.easedProgress(0.2) > 0.5)
    }

    @Test
    fun graphMappingRoundTrips() {
        val p = unitToGraph(0.3, 1.1, 192f, 192f)
        val (x, y) = graphToUnit(p, 192f, 192f)
        assertEquals(0.3, x, 1e-5)
        assertEquals(1.1, y, 1e-5)
    }
}
