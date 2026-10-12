package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

/** Roadmap item 134: the skeleton's dopesheet and angle curves. */
class DopesheetTest {
    private fun rig(): CanvasState = CanvasState().apply {
        loadStrokes(listOf(Stroke(Color.Black, 2.0, boneId = "c", animFrame = 0).apply { addPoint(WorldPoint(15.0, 0.0)); addPoint(WorldPoint(20.0, 0.0)) }))
        loadBones(listOf(
            Bone("r", null, WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), 0),
            Bone("c", "r", WorldPoint(10.0, 0.0), WorldPoint(20.0, 0.0), 0),
            Bone("r", null, WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), 1),
            Bone("c", "r", WorldPoint(10.0, 0.0), WorldPoint(10.0, 10.0), 1),
        ))
    }

    @Test
    fun tracksShowKeysWherePosesChange() {
        val tracks = rig().boneTimeline { "Bone $it" }
        assertEquals(listOf("Bone 1", "Bone 2"), tracks.map { it.label })
        assertEquals(listOf(true, false), tracks[0].keys) // the root holds in frame 2
        assertEquals(listOf(true, true), tracks[1].keys)
        assertEquals(0.0, tracks[1].angles[0]!!, 1e-9)
        assertEquals(PI / 2, tracks[1].angles[1]!!, 1e-9) // relative to the root
    }

    @Test
    fun settingAnAngleTurnsTheBoneAndItsInkInThatFrameAndUndoes() {
        val state = rig()
        state.setBoneAngle("c", 0, PI / 2)
        val child = state.bones.first { it.id == "c" && it.animFrame == 0 }
        assertEquals(10.0, child.tail.x, 1e-9)
        assertEquals(10.0, child.tail.y, 1e-9)
        val ink = state.strokes.single().points.last()
        assertEquals(10.0, ink.x, 1e-9)
        assertEquals(10.0, ink.y, 1e-9)
        // The other frame is untouched; frame 1 now holds frame 0's pose.
        assertEquals(listOf(true, false), state.boneTimeline { "$it" }[1].keys)
        state.undo()
        assertEquals(20.0, state.bones.first { it.id == "c" && it.animFrame == 0 }.tail.x, 1e-9)
    }
}
