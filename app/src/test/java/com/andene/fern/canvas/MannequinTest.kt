package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 108 (2D): the posable mannequin. */
class MannequinTest {
    @Test
    fun aRiggedFigureWithEveryLimbBoundAndPosable() {
        val state = CanvasState().apply { viewportSize = Size(800f, 1000f) }
        state.insertMannequin()
        assertEquals(15, state.bones.size) // spine, neck, head; per side shoulder, two arm bones, hip, two leg bones
        assertEquals(11, state.strokes.size) // every bone but the four joints
        val boneIds = state.bones.map { it.id }.toSet()
        assertTrue(state.strokes.all { it.boneId in boneIds })
        assertEquals(Tool.BONE, state.activeTool)
        // Raise the right forearm about the elbow: its limb moves, the rest of the body doesn't.
        val forearm = state.bones.single { b -> b.parentId != null && state.bones.none { it.parentId == b.id } && b.tail.x > state.panWorld.x && b.tail.y < state.panWorld.y + 100 }
        val before = state.strokes.associate { it.boneId to it.points.toList() }
        state.beginBone(forearm.tail)
        state.continueBone(WorldPoint(forearm.head.x + 200, forearm.head.y))
        state.endBone()
        val moved = state.strokes.filter { it.points.toList() != before[it.boneId] }.map { it.boneId }
        assertTrue(forearm.id in moved)
        assertTrue(moved.size <= 2) // the forearm (and, with smooth joints, the upper arm near the elbow)
        state.undo()
        assertTrue(state.strokes.all { it.points.toList() == before[it.boneId] })
    }
}
