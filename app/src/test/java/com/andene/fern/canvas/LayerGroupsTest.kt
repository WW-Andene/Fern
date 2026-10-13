package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D43: layer groups, moving into and out of them, and acting on several layers at once. */
@RunWith(RobolectricTestRunner::class)
class LayerGroupsTest {
    private fun layer(id: String, group: String? = null) = Layer(id, id, group = group)

    @Test
    fun movingJoinsAGroupInsideItAndLeavesPastItsEdge() {
        // Between two members: joins.
        assertEquals("g", groupAfterMove(null, layer("a", "g"), layer("b", "g")))
        // At the group's edge, an outsider stays outside; a member stays in.
        assertNull(groupAfterMove(null, layer("a"), layer("b", "g")))
        assertEquals("g", groupAfterMove("g", layer("a"), layer("b", "g")))
        assertEquals("g", groupAfterMove("g", null, layer("b", "g")))
        // Past the last member: leaves.
        assertNull(groupAfterMove("g", layer("a"), null))
        assertNull(groupAfterMove("g", layer("a"), layer("b")))
    }

    @Test
    fun groupingGathersTheLayersWhereTheTopmostWas() {
        val state = CanvasState()
        state.loadLayers(listOf(layer("a"), layer("b"), layer("c"), layer("d")))
        state.groupLayers(setOf("a", "c"), "g")
        assertEquals(listOf("b", "a", "c", "d"), state.layers.map { it.id })
        assertEquals(listOf(null, "g", "g", null), state.layers.map { it.group })
        assertEquals(1..2, state.groupRun("a"))
    }

    @Test
    fun aLayerDraggedThroughAGroupJoinsThenLeavesIt() {
        val state = CanvasState()
        state.loadLayers(listOf(layer("x"), layer("a", "g"), layer("b", "g"), layer("y")))
        state.moveLayer("x", up = true) // a, x, b: between two members, inside
        assertEquals("g", state.layers.first { it.id == "x" }.group)
        state.moveLayer("x", up = true) // a, b, x: the group's top member now, still inside
        assertEquals("g", state.layers.first { it.id == "x" }.group)
        state.moveLayer("x", up = true) // a, b, y, x: past the group's edge, out
        assertNull(state.layers.first { it.id == "x" }.group)
        assertEquals(listOf("a", "b", "y", "x"), state.layers.map { it.id })
    }

    @Test
    fun aGroupsEyeAndLockActOnEveryMember() {
        val state = CanvasState()
        state.loadLayers(listOf(layer("a", "g"), layer("b", "g"), layer("c")))
        state.setGroupVisible("a", false)
        state.setGroupLocked("b", true)
        assertEquals(listOf(false, false, true), state.layers.map { it.visible })
        assertEquals(listOf(true, true, false), state.layers.map { it.locked })
        state.ungroup("a")
        assertTrue(state.layers.all { it.group == null })
    }

    @Test
    fun deletingSeveralKeepsAtLeastOneLayer() {
        val state = CanvasState()
        state.loadLayers(listOf(layer("a"), layer("b"), layer("c")))
        state.deleteLayers(setOf("a", "b", "c"))
        assertEquals(3, state.layers.size)
        state.deleteLayers(setOf("a", "c"))
        assertEquals(listOf("b"), state.layers.map { it.id })
        assertEquals("b", state.activeLayerId)
    }
}
