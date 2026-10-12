package com.andene.fern.canvas

/** Most statements an object's script may run in one step of the game, so a runaway loop ends instead of freezing play. */
const val GAME_SCRIPT_MAX_STEPS = 10_000

/**
 * Game scripts (roadmap item 164): an object's script in the [ScriptEngine] language, run once
 * every step of the game, after movement and physics, sandboxed - it reaches only its own object
 * and what that object touches. Variables it sets keep their value from step to step.
 *
 * Read: `x`, `y` (its centre), `vx`, `vy` (its speed), `angle`, `scale`, `visible`, `grounded`,
 * `time` (seconds playing), `dt` (seconds this step), `name` and `state`.
 * Functions: `prop("score")`, `down("jump")` and `pressed("jump")` (input actions),
 * `touching("Coin")` (empty text: anything, walls included), `count("Coin")` (objects of that
 * name still playing).
 * Commands: `setprop "score" v`, `addprop "score" v`, `goto x y` (its centre), `speed vx vy`,
 * `turnto a`, `scaleto s`, `show`, `hide`, `remove`, `removetouching "Coin"`, `setstate "run"`.
 */
object GameScript {
    val commands = mapOf(
        "setprop" to 2, "addprop" to 2, "goto" to 2, "speed" to 2, "turnto" to 1, "scaleto" to 1,
        "show" to 0, "hide" to 0, "remove" to 0, "removetouching" to 1, "setstate" to 1,
    )

    val functions = mapOf("prop" to 1, "down" to 1, "pressed" to 1, "touching" to 1, "count" to 1)

    /** [source] parsed, or the error that stops it parsing. */
    fun parse(source: String): ScriptProgram = ScriptEngine.parse(source, commands, functions)

    /** Null when [source] parses, else its first error. */
    fun check(source: String): ScriptError? = try {
        parse(source)
        null
    } catch (e: ScriptError) {
        e
    }
}

/** One object's script while the game plays: the program, the object, and the script's own variables. */
class GameScriptRunner(private val program: ScriptProgram, private val o: GameInstance) {
    private val variables = HashMap<String, Any>()

    /** Runs the script for one step of [world], [dt] seconds long. Throws [ScriptError]. */
    fun step(world: GameWorld, dt: Double) {
        ScriptEngine.run(program, Host(world, dt), variables, GAME_SCRIPT_MAX_STEPS)
    }

    private inner class Host(private val world: GameWorld, private val dt: Double) : ScriptHost {
        override val commands get() = GameScript.commands
        override val functions get() = GameScript.functions

        private fun bool(b: Boolean) = if (b) 1.0 else 0.0

        override fun variable(name: String): Any? = when (name) {
            "x" -> o.centre.x
            "y" -> o.centre.y
            "vx" -> o.vx
            "vy" -> o.vy
            "angle" -> o.angle
            "scale" -> o.scale
            "visible" -> bool(o.visible)
            "grounded" -> bool(o.grounded)
            "time" -> o.time
            "dt" -> dt
            "name" -> o.def.name
            "state" -> o.state.orEmpty()
            else -> null
        }

        override fun function(name: String, args: List<Any>, line: Int): Any {
            val text = ScriptEngine.text(args[0], line)
            return when (name) {
                "prop" -> o.properties[text] ?: 0.0
                "down" -> bool(world.input.isDown(text))
                "pressed" -> bool(world.input.justPressed(text))
                "touching" -> bool(conditionHolds(Condition(ConditionKind.TOUCHING, text), o, world))
                else -> world.instances.count { !it.removed && it.def.name == text }.toDouble() // count
            }
        }

        override fun command(name: String, args: List<Any>, line: Int) {
            fun n(i: Int) = ScriptEngine.number(args[i], line)
            fun t(i: Int) = ScriptEngine.text(args[i], line)
            when (name) {
                "setprop" -> GameActions.setProperty(o, t(0), n(1))
                "addprop" -> GameActions.addToProperty(o, t(0), n(1))
                "goto" -> {
                    val centre = o.centre
                    o.x += n(0) - centre.x
                    o.y += n(1) - centre.y
                }
                "speed" -> {
                    o.vx = n(0)
                    o.vy = n(1)
                }
                "turnto" -> o.angle = n(0) % 360
                "scaleto" -> o.scale = n(0).takeIf { it > 0 } ?: throw ScriptError(line, "scale must be above 0")
                "show" -> o.visible = true
                "hide" -> o.visible = false
                "remove" -> GameActions.remove(o)
                "removetouching" -> GameActions.removeTouched(world, o, t(0))
                "setstate" -> world.goToState(o, t(0))
            }
        }
    }
}
