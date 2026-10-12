package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 153: branches and frame-by-frame merges of an animation. */
@RunWith(RobolectricTestRunner::class)
class AnimationBranchesTest {
    private fun dot(frame: Int, x: Double, color: Color = Color.Black) =
        Stroke(color, 2.0, animFrame = frame).apply { addPoint(WorldPoint(x, 0.0)); addPoint(WorldPoint(x + 1, 0.0)) }

    /** A three-frame animation, one dot per frame. */
    private fun animation() = CanvasState().apply { loadStrokes(listOf(dot(0, 0.0), dot(1, 10.0), dot(2, 20.0))) }

    private fun CanvasState.redraw(frame: Int, color: Color) {
        val at = strokes.indexOfFirst { it.animFrame == frame }
        strokes[at] = strokes[at].withColor(color)
    }

    @Test
    fun switchingBranchesSwapsTheAnimation() {
        val state = animation()
        state.createBranch("take 2")
        assertEquals(listOf("main", "take 2"), state.branches.map { it.name })
        state.redraw(1, Color.Red)
        val main = state.branches.first { it.name == "main" }.id
        state.switchBranch(main)
        assertEquals(Color.Black, state.strokes.single { it.animFrame == 1 }.color)
        state.switchBranch(state.branches.first { it.name == "take 2" }.id)
        assertEquals(Color.Red, state.strokes.single { it.animFrame == 1 }.color)
    }

    @Test
    fun mergingTakesTheirChangesAndSettlesConflictsAsAsked() {
        val state = animation()
        state.createBranch("idea")
        val idea = state.currentBranchId!!
        state.redraw(1, Color.Red) // only the branch changes frame 1
        state.redraw(2, Color.Green) // both will change frame 2
        state.switchBranch(state.branches.first { it.name == "main" }.id)
        state.redraw(2, Color.Blue)
        state.redraw(0, Color.Gray) // only main changes frame 0
        val kept = state.mergeBranch(idea, MergePreference.OURS)
        assertEquals(listOf(1), kept.taken)
        assertEquals(listOf(2), kept.conflicts)
        assertEquals(listOf(Color.Gray, Color.Red, Color.Blue), (0..2).map { f -> state.strokes.single { it.animFrame == f }.color })
        state.undo()
        assertEquals(Color.Black, state.strokes.single { it.animFrame == 1 }.color)
        val taken = state.mergeBranch(idea, MergePreference.THEIRS)
        assertEquals(listOf(1, 2), taken.taken)
        assertEquals(Color.Green, state.strokes.single { it.animFrame == 2 }.color)
    }

    @Test
    fun framesAddedOnABranchComeAcross() {
        val state = animation()
        state.createBranch("longer")
        val longer = state.currentBranchId!!
        state.goToAnimFrame(2)
        state.insertAnimFrame(copyCurrent = true)
        assertEquals(4, state.animFrameCount)
        state.switchBranch(state.branches.first { it.name == "main" }.id)
        assertEquals(3, state.animFrameCount)
        val report = state.mergeBranch(longer, MergePreference.OURS)
        assertEquals(listOf(3), report.taken)
        assertEquals(4, state.animFrameCount)
    }

    @Test
    fun theBranchesFileRoundTrips() {
        val state = animation()
        state.createBranch("b")
        val bytes = AnimationBranchFile.write(state.branches.toList(), state.currentBranchId!!)
        val (branches, current) = AnimationBranchFile.read(bytes)
        assertEquals(state.currentBranchId, current)
        assertEquals(state.branches.map { it.name }, branches.map { it.name })
        assertEquals(3, branches.last().head.frameCount)
        assertTrue(branches.last().head.frameKey(1) == state.animationSnapshot().frameKey(1))
        assertTrue(runCatching { AnimationBranchFile.read(bytes.copyOf(10)) }.isFailure)
    }
}
