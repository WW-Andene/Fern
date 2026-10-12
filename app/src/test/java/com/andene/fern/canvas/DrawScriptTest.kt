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

    @Test
    fun variablesConditionsLoopsAndProcedures() {
        val lines = drawn(
            """
            to polygon sides size [
              repeat sides [ forward size turn 360 / sides ]
            ]
            set n 3
            while n <= 5 [
              if n % 2 == 0 [ color #0000FF ] else [ color #FF0000 ]
              polygon n 10 * n
              move 0 -5
              set n n + 1
            ]
            """.trimIndent(),
        )
        assertEquals(3, lines.size)
        assertEquals(listOf(4, 5, 6), lines.map { it.points.size })
        assertEquals(listOf(Color.Red, Color.Blue, Color.Red), lines.map { it.color })
        assertEquals(WorldPoint(0.0, -5.0), lines[1].points.first())
    }

    @Test
    fun expressionsReadThePen() {
        val line = drawn("forward 2 * (3 + 4) - 4 turn 90 forward sqrt(16) + max(1, xcor)").single()
        assertEquals(10.0, line.points.last().x, 1e-9)
        assertEquals(14.0, line.points.last().y, 1e-9)
    }

    @Test
    fun runtimeErrorsNameTheirLine() {
        val unknown = DrawScript.run("forward 1\nforward size") as ScriptResult.Failed
        assertEquals(2, unknown.line)
        assertTrue(unknown.message.contains("size"))
        assertTrue((DrawScript.run("forward 1 / 0") as ScriptResult.Failed).message.contains("zero"))
        assertTrue((DrawScript.run("to f [ f ]\nf") as ScriptResult.Failed).message.contains("deep"))
        assertTrue((DrawScript.run("while 1 [ ]") as ScriptResult.Failed).message.contains("steps"))
    }
}
