package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/** Roadmap item 135: procedural sway of a bone across the animation. */
class ProceduralMotionTest {
    @Test
    fun sineSwingsEvenlyAndWiggleIsSmoothAndRepeatable() {
        assertEquals(0.0, proceduralSway(SwayKind.SINE, 0, 1.0, 8.0), 1e-9)
        assertEquals(1.0, proceduralSway(SwayKind.SINE, 2, 1.0, 8.0), 1e-9) // a quarter period in
        assertEquals(-1.0, proceduralSway(SwayKind.SINE, 6, 1.0, 8.0), 1e-9)
        val wiggle = (0..64).map { proceduralSway(SwayKind.WIGGLE, it, 0.5, 4.0, seed = 7) }
        assertTrue(wiggle.all { abs(it) <= 0.5 + 1e-9 })
        assertTrue(wiggle.zipWithNext().all { (a, b) -> abs(b - a) < 0.5 }) // no jumps between frames
        assertTrue(wiggle.distinct().size > 10)
        assertEquals(wiggle, (0..64).map { proceduralSway(SwayKind.WIGGLE, it, 0.5, 4.0, seed = 7) })
    }

    @Test
    fun swayTurnsTheBoneInEveryFrameOnTopOfItsPose() {
        val state = CanvasState()
        state.loadBones((0 until 4).map { Bone("b", null, WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0), it) })
        state.swayBone("b") { frame -> proceduralSway(SwayKind.SINE, frame, PI / 2, 4.0) }
        val angles = (0 until 4).map { f -> state.bones.first { it.animFrame == f }.angle }
        assertEquals(0.0, angles[0], 1e-9)
        assertEquals(PI / 2, angles[1], 1e-9)
        assertEquals(0.0, angles[2], 1e-9)
        assertEquals(-PI / 2, angles[3], 1e-9)
        state.undo()
        assertTrue(state.bones.all { abs(it.angle) < 1e-9 })
    }
}
