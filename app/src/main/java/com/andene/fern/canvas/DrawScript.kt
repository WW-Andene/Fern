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
 * Drawing scripts (roadmap items 111 and 164): a small turtle-graphics language that draws with
 * a pen moving over the page, run in a sandbox - it can only draw, never reach files, the
 * network or the rest of the app, and it stops after [SCRIPT_MAX_STEPS] steps. One command
 * per line or several separated by spaces; `//` starts a comment (`#` belongs to colours).
 *
 * - `forward d` / `back d`: move the pen d units, drawing while it's down.
 * - `turn a`: turn a degrees clockwise (negative turns the other way).
 * - `move x y`: jump to (x, y) without drawing. `home`: back to (0, 0), heading right.
 * - `penup` / `pendown`: stop or start drawing.
 * - `color #RRGGBB` and `width w`: style of what's drawn next.
 * - `circle r`: a circle of radius r around the pen.
 * - `repeat n [ ... ]`: the commands in brackets n times; repeats nest.
 *
 * Numbers may be decimals. The pen starts at (0, 0) heading right, down, black, width 2.
 */
object DrawScript {
    private sealed interface Command {
        val line: Int
    }

    private data class Simple(override val line: Int, val name: String, val args: List<String>) : Command
    private data class Repeat(override val line: Int, val times: Int, val body: List<Command>) : Command

    private class ScriptError(val line: Int, message: String) : Exception(message)

    private data class Token(val text: String, val line: Int)

    private val arity = mapOf(
        "forward" to 1, "back" to 1, "turn" to 1, "move" to 2, "home" to 0, "penup" to 0, "pendown" to 0,
        "color" to 1, "width" to 1, "circle" to 1,
    )

    fun run(source: String): ScriptResult = try {
        ScriptResult.Drawn(execute(parse(tokenize(source))))
    } catch (e: ScriptError) {
        ScriptResult.Failed(e.line, e.message ?: "error")
    }

    private fun tokenize(source: String): List<Token> = source.lines().flatMapIndexed { index, raw ->
        raw.substringBefore("//").replace("[", " [ ").replace("]", " ] ").split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Token(it, index + 1) }
    }

    private fun parse(tokens: List<Token>): List<Command> {
        var i = 0
        fun block(nested: Boolean, openLine: Int): List<Command> {
            val commands = ArrayList<Command>()
            while (i < tokens.size) {
                val token = tokens[i++]
                when (val word = token.text.lowercase()) {
                    "]" -> if (nested) return commands else throw ScriptError(token.line, "unexpected ]")
                    "repeat" -> {
                        val count = tokens.getOrNull(i++) ?: throw ScriptError(token.line, "repeat needs a count")
                        val times = count.text.toIntOrNull()?.takeIf { it >= 0 } ?: throw ScriptError(count.line, "repeat count must be a whole number")
                        val open = tokens.getOrNull(i++)
                        if (open?.text != "[") throw ScriptError(open?.line ?: token.line, "repeat needs [ after its count")
                        commands.add(Repeat(token.line, times, block(nested = true, openLine = open.line)))
                    }
                    else -> {
                        val n = arity[word] ?: throw ScriptError(token.line, "unknown command '${token.text}'")
                        val args = (0 until n).map { tokens.getOrNull(i++)?.text ?: throw ScriptError(token.line, "$word needs $n value(s)") }
                        commands.add(Simple(token.line, word, args))
                    }
                }
            }
            if (nested) throw ScriptError(openLine, "missing ] for repeat")
            return commands
        }
        return block(nested = false, openLine = 0)
    }

    private fun execute(program: List<Command>): List<ScriptLine> {
        val lines = ArrayList<ScriptLine>()
        var x = 0.0
        var y = 0.0
        var heading = 0.0 // degrees, clockwise from +x on screen (y down)
        var down = true
        var color = Color.Black
        var width = 2.0
        var current: MutableList<WorldPoint>? = null
        var steps = 0
        var points = 0

        fun finish() {
            current?.takeIf { it.size >= 2 }?.let { lines.add(ScriptLine(color, width, it.toList())) }
            current = null
        }
        fun number(text: String, line: Int): Double =
            text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw ScriptError(line, "'$text' isn't a number")
        fun addPoint(p: WorldPoint, line: Int) {
            if (++points > SCRIPT_MAX_POINTS) throw ScriptError(line, "the script draws more than $SCRIPT_MAX_POINTS points")
            current!!.add(p)
        }
        fun step(distance: Double, line: Int) {
            val rad = heading * PI / 180
            val nx = x + cos(rad) * distance
            val ny = y + sin(rad) * distance
            if (down) {
                if (current == null) current = mutableListOf<WorldPoint>().also { it.add(WorldPoint(x, y)); points++ }
                addPoint(WorldPoint(nx, ny), line)
            }
            x = nx
            y = ny
        }

        fun runAll(commands: List<Command>) {
            for (command in commands) {
                if (++steps > SCRIPT_MAX_STEPS) throw ScriptError(command.line, "the script ran more than $SCRIPT_MAX_STEPS steps")
                when (command) {
                    // Each pass costs a step too, so even an empty loop can't run forever.
                    is Repeat -> repeat(command.times) {
                        if (++steps > SCRIPT_MAX_STEPS) throw ScriptError(command.line, "the script ran more than $SCRIPT_MAX_STEPS steps")
                        runAll(command.body)
                    }
                    is Simple -> when (command.name) {
                        "forward" -> step(number(command.args[0], command.line), command.line)
                        "back" -> step(-number(command.args[0], command.line), command.line)
                        "turn" -> heading = (heading + number(command.args[0], command.line)) % 360
                        "move" -> {
                            finish()
                            x = number(command.args[0], command.line)
                            y = number(command.args[1], command.line)
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
                            val hex = command.args[0].removePrefix("#")
                            val rgb = hex.takeIf { it.length == 6 }?.toLongOrNull(16) ?: throw ScriptError(command.line, "colour must be #RRGGBB")
                            color = Color(0xFF000000 or rgb)
                        }
                        "width" -> {
                            finish()
                            width = number(command.args[0], command.line).takeIf { it > 0 } ?: throw ScriptError(command.line, "width must be above 0")
                        }
                        "circle" -> {
                            val r = number(command.args[0], command.line)
                            finish()
                            val circle = (0..48).map { k ->
                                val a = 2 * PI * k / 48
                                WorldPoint(x + r * cos(a), y + r * sin(a))
                            }
                            points += circle.size
                            if (points > SCRIPT_MAX_POINTS) throw ScriptError(command.line, "the script draws more than $SCRIPT_MAX_POINTS points")
                            lines.add(ScriptLine(color, width, circle))
                        }
                    }
                }
            }
        }
        runAll(program)
        finish()
        return lines
    }
}
