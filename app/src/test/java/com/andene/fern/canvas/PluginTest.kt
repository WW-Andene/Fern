package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 111: plugins in sandboxed JavaScript. */
@RunWith(RobolectricTestRunner::class)
class PluginTest {
    private val input = PluginInput(
        listOf(PluginStroke(listOf(WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0)), Color.Red, 2.0, filled = false, closed = false)),
        WorldPoint(-100.0, -100.0) to WorldPoint(100.0, 100.0),
        Color.Black,
        3.0,
    )

    private fun plugin(source: String) = parsePlugin("t", source.trimIndent(), "Test", builtIn = false)

    private fun builtIn(name: String) = PluginLibrary.BUILT_IN.single { it.name == name }

    @Test
    fun headersDeclareNameDescriptionAndParameters() {
        val p = plugin(
            """
            // @name Stars
            // @description Draws stars.
            // @param points number 5 How many points
            function run() {}
            """,
        )
        assertEquals("Stars", p.name)
        assertEquals("Draws stars.", p.description)
        assertEquals(listOf(PluginParam("points", 5.0, "How many points")), p.params)
    }

    @Test
    fun builtInPluginsRun() {
        val scatter = runPlugin(builtIn("Scatter dots"), input, mapOf("count" to 12.0)) as PluginResult.Changes
        assertEquals(12, scatter.add.size)
        // Dots land in the selection's bounds.
        assertTrue(scatter.add.all { s -> s.points.all { it.x in 0.0..10.1 && it.y in -0.1..0.1 } })
        val spiral = runPlugin(builtIn("Spiral"), input, emptyMap()) as PluginResult.Changes
        assertTrue(spiral.add.single().points.size > 100)
        val colours = runPlugin(builtIn("Random colours"), input, emptyMap()) as PluginResult.Changes
        assertEquals(0, colours.update.single().index)
        val grid = runPlugin(builtIn("Grid of copies"), input, mapOf("columns" to 2.0, "rows" to 2.0)) as PluginResult.Changes
        assertEquals(3, grid.add.size)
        assertEquals(Color.Red, grid.add.first().color)
        val wobble = runPlugin(builtIn("Wobble"), input, emptyMap()) as PluginResult.Changes
        assertEquals(2, wobble.update.single().points!!.size)
    }

    @Test
    fun theSandboxKeepsPluginsAwayFromJavaAndEndsRunawayLoops() {
        val java = runPlugin(plugin("function run() { java.lang.System.exit(0); }"), input, emptyMap())
        assertTrue(java is PluginResult.Failed)
        // Neither Rhino's Java bridges exists in a plugin's scope.
        val bridges = runPlugin(plugin("function run() { if (typeof Packages !== 'undefined' || typeof java !== 'undefined') throw new Error('exposed'); return {}; }"), input, emptyMap())
        assertTrue(bridges is PluginResult.Changes)
        val loop = runPlugin(plugin("function run() { while (true) {} }"), input, emptyMap())
        assertTrue((loop as PluginResult.Failed).message.contains("instructions"))
        val missing = runPlugin(plugin("var x = 1;"), input, emptyMap())
        assertTrue((missing as PluginResult.Failed).message.contains("run"))
        val bad = runPlugin(plugin("function run() { return { update: [{ index: 7 }] }; }"), input, emptyMap())
        assertTrue(bad is PluginResult.Failed)
    }

    @Test
    fun changesApplyAsOneUndoStep() {
        val state = CanvasState()
        state.viewportSize = Size(400f, 400f)
        val stroke = Stroke(Color.Red, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(10.0, 0.0)) }
        val other = Stroke(Color.Blue, 2.0).apply { addPoint(WorldPoint(0.0, 5.0)); addPoint(WorldPoint(10.0, 5.0)) }
        state.loadStrokes(listOf(stroke, other))
        state.selectAllOnActiveLayer()
        val result = runPlugin(
            plugin(
                """
                function run(doc) {
                  return { update: [{ index: 0, color: '#00FF00', points: [[0, 0], [20, 0]] }], remove: [1],
                           add: [{ points: [[0, 9], [9, 9], [9, 0]], closed: true }] };
                }
                """,
            ),
            state.pluginInput(), emptyMap(),
        ) as PluginResult.Changes
        state.applyPluginChanges(result)
        assertEquals(2, state.strokes.size)
        assertEquals(Color(0xFF00FF00), state.strokes[0].color)
        assertEquals(20.0, state.strokes[0].maxX, 1e-9)
        assertEquals(state.strokes[1].points.first(), state.strokes[1].points.last()) // closed
        assertEquals(2, state.selection.size)
        state.undo()
        assertEquals(listOf(Color.Red, Color.Blue), state.strokes.map { it.color })
        assertEquals(10.0, state.strokes[0].maxX, 1e-9)
    }
}
