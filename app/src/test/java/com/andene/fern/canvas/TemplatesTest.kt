package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 72: the weekly planner and mind-map templates. */
class TemplatesTest {
    private fun state() = CanvasState().apply { viewportSize = Size(1000f, 800f) }

    @Test
    fun plannerHasEightLabelledBoxesInAPageFrameAndUndoes() {
        val state = state()
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        state.insertWeeklyPlanner("Week", days, "Notes")
        assertEquals(8, state.strokes.size)
        assertEquals(days + "Notes", state.textItems.map { it.text })
        val frame = state.frames.single()
        assertEquals("Week", frame.name)
        // Every box and label lies on the page.
        for (stroke in state.strokes) assertTrue(stroke.minX >= frame.min.x && stroke.maxX <= frame.max.x && stroke.minY >= frame.min.y && stroke.maxY <= frame.max.y)
        for (label in state.textItems) assertTrue(label.position.x > frame.min.x && label.position.y > frame.min.y)
        state.undo()
        assertEquals(0, state.strokes.size)
        assertEquals(0, state.textItems.size)
    }

    @Test
    fun mindMapJoinsTheCentreToFourBranchesAndUndoes() {
        val state = state()
        state.insertMindMap("Topic", "Idea")
        assertEquals(5, state.textItems.size)
        assertTrue(state.textItems.all { it.sticky })
        val central = state.textItems.single { it.text == "Topic" }
        assertEquals(4, state.connectors.size)
        assertTrue(state.connectors.all { it.fromId == central.id })
        assertEquals(state.textItems.filter { it.text == "Idea" }.map { it.id }.toSet(), state.connectors.map { it.toId }.toSet())
        // Every connector has room to be drawn: no branch overlaps the centre.
        for (connector in state.connectors) {
            val (from, to) = state.connectorEnds(connector)!!
            assertTrue(connectorSegment(from.first, from.second, to.first, to.second) != null)
        }
        state.undo()
        assertEquals(0, state.textItems.size)
        assertEquals(0, state.connectors.size)
    }
}
