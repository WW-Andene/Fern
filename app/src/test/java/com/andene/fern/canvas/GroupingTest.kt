package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class GroupingTest {
    private fun line(x: Double) = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(x, 0.0)); addPoint(WorldPoint(x, 10.0)) }

    private fun marquee(state: CanvasState, from: WorldPoint, to: WorldPoint) {
        state.beginSelectGesture(from)
        state.continueSelectGesture(to)
        state.endSelectGesture()
    }

    private fun groupedState(): CanvasState {
        val state = CanvasState()
        state.loadStrokes(listOf(line(0.0), line(100.0), line(200.0)))
        state.selection.addAll(state.strokes.take(2))
        state.groupSelection()
        state.selection.clear()
        return state
    }

    @Test
    fun selectingOneMemberSelectsTheWholeGroup() {
        val state = groupedState()
        marquee(state, WorldPoint(-5.0, -5.0), WorldPoint(5.0, 15.0)) // only touches the first line
        assertEquals(2, state.selection.size)
        assertTrue(state.selectionHasGroup)
        marquee(state, WorldPoint(195.0, -5.0), WorldPoint(205.0, 15.0)) // the ungrouped line
        assertEquals(1, state.selection.size)
    }

    @Test
    fun ungroupAndUndo() {
        val state = groupedState()
        marquee(state, WorldPoint(-5.0, -5.0), WorldPoint(5.0, 15.0))
        state.ungroupSelection()
        assertTrue(state.strokes.all { it.groupId == null })
        state.undo()
        assertEquals(2, state.strokes.count { it.groupId != null })
    }

    @Test
    fun duplicateMakesANewGroup() {
        val state = groupedState()
        marquee(state, WorldPoint(-5.0, -5.0), WorldPoint(5.0, 15.0))
        val original = state.selection.first().groupId
        state.duplicateSelection()
        val copies = state.selection.toList()
        assertEquals(2, copies.size)
        assertEquals(1, copies.map { it.groupId }.distinct().size) // still one group
        assertNotEquals(original, copies.first().groupId) // but not the original one
    }

    @Test
    fun groupNeedsTwoStrokes() {
        val state = CanvasState()
        state.loadStrokes(listOf(line(0.0)))
        state.selection.addAll(state.strokes)
        state.groupSelection()
        assertNull(state.strokes.single().groupId)
    }

    @Test
    fun groupIdIsSavedAndPartsOfAnErasedMemberStayGrouped() {
        val stroke = Stroke(Color.Black, 2.0, groupId = "g1").apply { (0..10).forEach { addPoint(WorldPoint(it.toDouble(), 0.0)) } }
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), listOf(stroke, line(5.0))) }.toByteArray()
        val decoded = StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals("g1", decoded[0].groupId)
        assertNull(decoded[1].groupId)
        val pieces = stroke.eraseNear(WorldPoint(5.0, 0.0), 1.5)
        assertNotNull(pieces)
        assertTrue(pieces!!.all { it.groupId == "g1" })
    }
}
