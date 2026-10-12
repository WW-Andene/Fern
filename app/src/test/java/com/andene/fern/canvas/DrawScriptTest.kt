package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap items 111 and 164: the sandboxed drawing script language. */
class DrawScriptTest {
    private fun drawn(source: String) = (DrawScript.run(source) as ScriptResult.Drawn).lines

    @Test
    fun aSquareFromARepeat() {
        val lines = drawn("color #FF0000 width 3\nrepeat 4 [ forward 100 turn 90 ]")
        val square = lines.single()
        assertEquals(Color.Red, square.color)
        assertEquals(3.0, square.width, 0.0)
        assertEquals(5, square.points.size)
        val end = square.points.last()
        assertEquals(0.0, end.x, 1e-9)
        assertEquals(0.0, end.y, 1e-9)
        assertEquals(100.0, square.points[2].y, 1e-9) // turning clockwise on screen goes down
    }

    @Test
    fun penUpAndMoveStartNewLinesAndCirclesAreTheirOwn() {
        val lines = drawn("forward 10 penup forward 10 pendown forward 10 // two dashes\nmove 50 50 circle 5")
        assertEquals(3, lines.size)
        assertEquals(WorldPoint(20.0, 0.0), lines[1].points.first())
        assertEquals(49, lines[2].points.size)
    }

    @Test
    fun errorsNameTheirLine() {
        val unknown = DrawScript.run("forward 10\njump 5") as ScriptResult.Failed
        assertEquals(2, unknown.line)
        assertTrue(unknown.message.contains("jump"))
        assertEquals(1, (DrawScript.run("repeat 3 [ forward 1") as ScriptResult.Failed).line)
        assertTrue(DrawScript.run("forward ten") is ScriptResult.Failed)
    }

    @Test
    fun runawayScriptsStop() {
        val result = DrawScript.run("repeat 100000 [ repeat 100000 [ ] ]") as ScriptResult.Failed
        assertTrue(result.message.contains("steps"))
    }
}
