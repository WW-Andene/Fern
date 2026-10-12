package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.ScriptableObject

/*
 * Plugins (roadmap item 111): JavaScript that reads the selection and draws, edits or removes
 * strokes, run by Mozilla Rhino in a sandbox. A plugin sees only the data handed to it - no Java
 * classes, files, network or app internals - and is stopped after [PLUGIN_MAX_INSTRUCTIONS]
 * instructions. It defines `run(doc, params)` and returns what to change; the app applies that
 * as one undo step.
 *
 * `doc` is { selection: [stroke...], bounds: {x, y, width, height} or null (the selection's),
 * view: {x, y, width, height} (what's on screen), color: "#RRGGBB" and width (the active pen) },
 * a stroke being { points: [[x, y]...], color: "#RRGGBB", opacity (0-1), width, filled, closed }.
 * The result may hold `add` (strokes as above; colour, width and the rest default to the active
 * pen's), `update` ({ index, points?, color?, opacity?, width? }, index into the selection) and
 * `remove` (selection indices).
 */

/** A plugin's parameter, declared in its header: `// @param name number 10 Label text`. */
data class PluginParam(val name: String, val default: Double, val label: String)

/** A plugin: its [name], [description] and [params] from its header comments, and its [source]. */
data class Plugin(val id: String, val name: String, val description: String, val params: List<PluginParam>, val source: String, val builtIn: Boolean)

/** Reads a plugin's header (`// @name`, `// @description`, `// @param`) from [source]; the name falls back to [fallbackName]. */
fun parsePlugin(id: String, source: String, fallbackName: String, builtIn: Boolean): Plugin {
    var name = fallbackName
    var description = ""
    val params = ArrayList<PluginParam>()
    for (line in source.lineSequence().map { it.trim() }.takeWhile { it.isEmpty() || it.startsWith("//") }) {
        val body = line.removePrefix("//").trim()
        when {
            body.startsWith("@name ") -> name = body.removePrefix("@name ").trim()
            body.startsWith("@description ") -> description = body.removePrefix("@description ").trim()
            body.startsWith("@param ") -> {
                val parts = body.removePrefix("@param ").trim().split(Regex("\\s+"), limit = 4)
                if (parts.size >= 3 && parts[1] == "number") {
                    params.add(PluginParam(parts[0], parts[2].toDoubleOrNull() ?: 0.0, parts.getOrNull(3) ?: parts[0]))
                }
            }
        }
    }
    return Plugin(id, name, description, params, source, builtIn)
}

/** A stroke as a plugin sees and returns it. */
data class PluginStroke(val points: List<WorldPoint>, val color: Color, val width: Double, val filled: Boolean, val closed: Boolean)

/** An edit a plugin asks for on selection stroke [index]; null fields stay as they are. */
data class PluginUpdate(val index: Int, val points: List<WorldPoint>?, val color: Color?, val width: Double?)

/** What a plugin run gives: its changes, or the error that stopped it. */
sealed interface PluginResult {
    data class Changes(val add: List<PluginStroke>, val update: List<PluginUpdate>, val remove: Set<Int>) : PluginResult
    data class Failed(val message: String) : PluginResult
}

/** What a plugin is given: the selection, its bounds, the view, and the active pen. */
class PluginInput(val selection: List<PluginStroke>, val view: Pair<WorldPoint, WorldPoint>, val activeColor: Color, val activeWidth: Double)

/** Runs [plugin] on [input] with [params] (missing ones take their defaults). */
fun runPlugin(plugin: Plugin, input: PluginInput, params: Map<String, Double>): PluginResult {
    val doc = JSONObject()
        .put("selection", JSONArray(input.selection.map(::strokeJson)))
        .put("bounds", boundsJson(input.selection.flatMap { it.points }))
        .put("view", rectJson(input.view.first, input.view.second))
        .put("color", hex(input.activeColor))
        .put("width", input.activeWidth)
    val values = JSONObject()
    for (param in plugin.params) values.put(param.name, params[param.name] ?: param.default)
    val output = try {
        SandboxFactory.call { cx ->
            val scope = cx.initSafeStandardObjects()
            ScriptableObject.putProperty(scope, "__doc", doc.toString())
            ScriptableObject.putProperty(scope, "__params", values.toString())
            cx.evaluateString(scope, plugin.source, plugin.name, 1, null)
            val wrapper = "(function () { if (typeof run !== 'function') throw new Error('the plugin has no run(doc, params) function');" +
                " var r = run(JSON.parse(__doc), JSON.parse(__params)); return r === undefined || r === null ? '{}' : JSON.stringify(r); })()"
            Context.toString(cx.evaluateString(scope, wrapper, "run", 1, null))
        }
    } catch (e: RhinoException) {
        return PluginResult.Failed(e.details() + (e.lineNumber().takeIf { it > 0 }?.let { " (line $it)" } ?: ""))
    } catch (e: PluginLimitException) {
        return PluginResult.Failed(e.message ?: "limit")
    } catch (e: RuntimeException) {
        return PluginResult.Failed(e.message ?: e.javaClass.simpleName)
    }
    return try {
        parseChanges(JSONObject(output), input)
    } catch (e: Exception) {
        PluginResult.Failed("the plugin returned something unexpected: ${e.message}")
    }
}

private fun parseChanges(json: JSONObject, input: PluginInput): PluginResult {
    var points = 0
    fun pointsOf(array: JSONArray?): List<WorldPoint>? {
        array ?: return null
        val list = (0 until array.length()).map { i ->
            val p = array.getJSONArray(i)
            WorldPoint(p.getDouble(0), p.getDouble(1)).also { if (!it.x.isFinite() || !it.y.isFinite()) error("a point isn't a finite number") }
        }
        points += list.size
        if (points > PLUGIN_MAX_POINTS) throw PluginLimitException("the plugin returned more than $PLUGIN_MAX_POINTS points")
        return list
    }
    fun colorOf(o: JSONObject): Color? {
        val text = o.optString("color").takeIf { it.isNotEmpty() } ?: return null
        val rgb = text.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16) ?: error("colour '$text' isn't #RRGGBB")
        val opacity = o.optDouble("opacity", 1.0).coerceIn(0.0, 1.0)
        return Color(0xFF000000 or rgb).copy(alpha = opacity.toFloat())
    }
    val add = json.optJSONArray("add")?.let { array ->
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val pts = pointsOf(o.getJSONArray("points")) ?: emptyList()
            PluginStroke(pts, colorOf(o) ?: input.activeColor, o.optDouble("width", input.activeWidth).coerceIn(0.01, 10_000.0), o.optBoolean("filled"), o.optBoolean("closed"))
        }.filter { it.points.isNotEmpty() }
    }.orEmpty()
    val update = json.optJSONArray("update")?.let { array ->
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val index = o.getInt("index")
            if (index !in input.selection.indices) error("update index $index isn't in the selection")
            PluginUpdate(index, pointsOf(o.optJSONArray("points")), colorOf(o), if (o.has("width")) o.getDouble("width").coerceIn(0.01, 10_000.0) else null)
        }
    }.orEmpty()
    val remove = json.optJSONArray("remove")?.let { array -> (0 until array.length()).map { array.getInt(it) }.filter { it in input.selection.indices }.toSet() }.orEmpty()
    return PluginResult.Changes(add, update, remove)
}

private fun strokeJson(s: PluginStroke) = JSONObject()
    .put("points", JSONArray(s.points.map { JSONArray().put(it.x).put(it.y) }))
    .put("color", hex(s.color)).put("opacity", s.color.alpha.toDouble())
    .put("width", s.width).put("filled", s.filled).put("closed", s.closed)

private fun boundsJson(points: List<WorldPoint>): Any =
    if (points.isEmpty()) JSONObject.NULL else rectJson(WorldPoint(points.minOf { it.x }, points.minOf { it.y }), WorldPoint(points.maxOf { it.x }, points.maxOf { it.y }))

private fun rectJson(min: WorldPoint, max: WorldPoint) = JSONObject().put("x", min.x).put("y", min.y).put("width", max.x - min.x).put("height", max.y - min.y)

private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)

/** A plugin went over a limit. */
class PluginLimitException(message: String) : RuntimeException(message)

/**
 * Rhino contexts for plugins: interpreted (as Android requires), no Java classes visible
 * (a class shutter that admits none), and an instruction count that ends a runaway script.
 */
private object SandboxFactory : ContextFactory() {
    private class Counted(factory: ContextFactory) : Context(factory) {
        var instructions = 0L
    }

    override fun makeContext(): Context = Counted(this).apply {
        optimizationLevel = -1
        languageVersion = Context.VERSION_ES6
        instructionObserverThreshold = INSTRUCTION_CHECK_EVERY
        setClassShutter { false }
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        val counted = cx as Counted
        counted.instructions += instructionCount
        if (counted.instructions > PLUGIN_MAX_INSTRUCTIONS) throw PluginLimitException("the plugin ran more than $PLUGIN_MAX_INSTRUCTIONS instructions")
    }

    fun <T> call(block: (Context) -> T): T {
        val cx = enterContext()
        try {
            return block(cx)
        } finally {
            Context.exit()
        }
    }

    private const val INSTRUCTION_CHECK_EVERY = 10_000
}

/** How much work one plugin run may do before it's stopped. */
const val PLUGIN_MAX_INSTRUCTIONS = 20_000_000L

/** How many points one plugin run may return. */
const val PLUGIN_MAX_POINTS = 200_000
