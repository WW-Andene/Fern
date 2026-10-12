package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** [CanvasState.strokesNear] must keep matching a brute-force search while the index is patched instead of rebuilt. */
class StrokesNearTest {
    private val random = Random(7)

    private fun stroke(x: Double, y: Double) = Stroke(Color.Black, 2.0).apply {
        addPoint(WorldPoint(x, y))
        addPoint(WorldPoint(x + 5, y + 5))
    }

    private fun check(state: CanvasState) {
        repeat(20) {
            val left = random.nextDouble(-50.0, 1000.0)
            val top = random.nextDouble(-50.0, 1000.0)
            val right = left + random.nextDouble(1.0, 200.0)
            val bottom = top + random.nextDouble(1.0, 200.0)
            val expected = state.strokes.filter { it.intersects(left, top, right, bottom) }
            val actual = state.strokesNear(left, top, right, bottom).filter { it.intersects(left, top, right, bottom) }
            assertEquals(expected, actual)
        }
    }

    @Test
    fun appendsRemovalsReplacementsAndInPlaceMovesStayCorrect() {
        val state = CanvasState()
        state.loadStrokes(List(2000) { stroke(random.nextDouble(0.0, 1000.0), random.nextDouble(0.0, 1000.0)) })
        check(state)
        state.strokes.add(stroke(10.0, 10.0)) // appended
        check(state)
        state.strokes.removeAt(500) // removed from the middle
        state.strokes.removeAt(1500)
        check(state)
        state.strokes[100] = stroke(900.0, 900.0) // replaced
        check(state)
        state.strokes[1200].shiftBy(WorldPoint(-300.0, -300.0)) // moved in place, same list
        state.strokes.add(0, stroke(500.0, 500.0))
        check(state)
        state.strokes.addAll(List(5000) { stroke(random.nextDouble(0.0, 1000.0), random.nextDouble(0.0, 1000.0)) }) // past the patch limit: rebuilt
        check(state)
    }
}
