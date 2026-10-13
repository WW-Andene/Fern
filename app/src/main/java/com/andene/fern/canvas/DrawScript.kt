package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Most drawing steps one script may take, so a runaway loop ends instead of freezing the app. */
const val SCRIPT_MAX_STEPS = 200_000

/** Most points a script may draw in total. */
const val SCRIPT_MAX_POINTS = 200_000

/** A line drawn by a script: colour, width and points, in the script's own units. */
data class ScriptLine(val color: Color, val width: Double, val points: List<WorldPoint>)

/** What running a script gives: the lines it drew, or the first error and the line it's on (1-based). */
sealed interface ScriptResult {
    data class Drawn(val lines: List<ScriptLine>) : ScriptResult
    data class Failed(val line: Int, val message: String) : ScriptResult
}

/**
 * Drawing scripts (roadmap items 111 and 164): a turtle-graphics host for [ScriptEngine] that
 * draws with a pen moving over the page, run in a sandbox - it can only draw, never reach files,
 * the network or the rest of the app, and it stops after [SCRIPT_MAX_STEPS] steps. The language
 * gives variables, arithmetic, `if`/`else`, `while`, `repeat` and procedures; this adds:
 *
 * - `forward d` / `back d`: move the pen d units, drawing while it's down.
 * - `turn a`: turn a degrees clockwise (negative turns the other way). `setheading a`: face a degrees.
 * - `move x y`: jump to (x, y) without drawing. `home`: back to (0, 0), heading right.
 * - `penup` / `pendown`: stop or start drawing.
 * - `color #RRGGBB` and `width w`: style of what's drawn next.
 * - `circle r`: a circle of radius r around the pen.
 * - `xcor`, `ycor` and `heading`: where the pen is and which way it faces.
 *
 * The pen starts at (0, 0) heading right, down, black, width 2.
 */
object DrawScript {
    private val commands = mapOf(
        "forward" to 1, "back" to 1, "turn" to 1, "setheading" to 1, "move" to 2, "home" to 0, "penup" to 0, "pendown" to 0,
        "color" to 1, "width" to 1, "circle" to 1,
    )

    /** The first error that stops [source] parsing, or null; cheap enough to run on every keystroke (design roadmap D33). */
    fun check(source: String): ScriptError? = try {
        ScriptEngine.parse(source, commands, emptyMap())
        null
    } catch (e: ScriptError) {
        e
    }

    fun run(source: String): ScriptResult = try {
        val program = ScriptEngine.parse(source, commands, emptyMap())
        val pen = Pen()
        ScriptEngine.run(program, pen, HashMap(), SCRIPT_MAX_STEPS)
        pen.finish()
        ScriptResult.Drawn(pen.lines)
    } catch (e: ScriptError) {
        ScriptResult.Failed(e.line, e.message ?: "error")
    }

    private class Pen : ScriptHost {
        override val commands get() = DrawScript.commands
        override val functions = emptyMap<String, Int>()
        val lines = ArrayList<ScriptLine>()
        private var x = 0.0
        private var y = 0.0
        private var heading = 0.0 // degrees, clockwise from +x on screen (y down)
        private var down = true
        private var color = Color.Black
        private var width = 2.0
        private var current: MutableList<WorldPoint>? = null
        private var points = 0

        fun finish() {
            current?.takeIf { it.size >= 2 }?.let { lines.add(ScriptLine(color, width, it.toList())) }
            current = null
        }

        private fun count(n: Int, line: Int) {
            points += n
            if (points > SCRIPT_MAX_POINTS) throw ScriptError(line, "the script draws more than $SCRIPT_MAX_POINTS points")
        }

        private fun step(distance: Double, line: Int) {
            val rad = heading * PI / 180
            val nx = x + cos(rad) * distance
            val ny = y + sin(rad) * distance
            if (down) {
                val path = current ?: mutableListOf(WorldPoint(x, y)).also { current = it; count(1, line) }
                count(1, line)
                path.add(WorldPoint(nx, ny))
            }
            x = nx
            y = ny
        }

        override fun variable(name: String): Any? = when (name) {
            "xcor" -> x
            "ycor" -> y
            "heading" -> heading
            else -> null
        }

        override fun function(name: String, args: List<Any>, line: Int): Any = throw ScriptError(line, "unknown function '$name'")

        override fun command(name: String, args: List<Any>, line: Int) {
            fun n(i: Int) = ScriptEngine.number(args[i], line)
            when (name) {
                "forward" -> step(n(0), line)
                "back" -> step(-n(0), line)
                "turn" -> heading = (heading + n(0)) % 360
                "setheading" -> heading = n(0) % 360
                "move" -> {
                    finish()
                    x = n(0)
                    y = n(1)
                }
                "home" -> {
                    finish()
                    x = 0.0
                    y = 0.0
                    heading = 0.0
                }
                "penup" -> {
                    finish()
                    down = false
                }
                "pendown" -> down = true
                "color" -> {
                    finish()
                    val hex = ScriptEngine.text(args[0], line).removePrefix("#")
                    val rgb = hex.takeIf { it.length == 6 }?.toLongOrNull(16) ?: throw ScriptError(line, "colour must be #RRGGBB")
                    color = Color(0xFF000000 or rgb)
                }
                "width" -> {
                    finish()
                    width = n(0).takeIf { it > 0 } ?: throw ScriptError(line, "width must be above 0")
                }
                "circle" -> {
                    val r = n(0)
                    finish()
                    val circle = (0..48).map { k ->
                        val a = 2 * PI * k / 48
                        WorldPoint(x + r * cos(a), y + r * sin(a))
                    }
                    count(circle.size, line)
                    lines.add(ScriptLine(color, width, circle))
                }
            }
        }
    }
}
