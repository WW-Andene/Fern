package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Roadmap item 83: drawing, chaining, binding and posing bones, frame by frame, saved and undoable. */
@RunWith(RobolectricTestRunner::class)
class BonesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun drag(state: CanvasState, from: WorldPoint, to: WorldPoint) {
        state.beginBone(from)
        state.continueBone(WorldPoint((from.x + to.x) / 2, (from.y + to.y) / 2))
        state.continueBone(to)
        state.endBone()
    }

    /** An arm: upper bone (0,0)-(100,0), forearm chained at its tail to (200,0), a stroke along the forearm. */
    private fun arm(): CanvasState {
        val state = CanvasState()
        state.activeTool = Tool.BONE
        drag(state, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))
        drag(state, WorldPoint(102.0, 1.0), WorldPoint(200.0, 0.0)) // starts near the tail: chained
        state.loadStrokes(listOf(Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(150.0, 0.0)); addPoint(WorldPoint(190.0, 0.0)) }))
        state.bindStrokesToNearestBones()
        return state
    }

    @Test
    fun aBoneStartedAtATailIsChainedAndSnapped() {
        val state = arm()
        val (upper, fore) = state.bones
        assertNull(upper.parentId)
        assertEquals(upper.id, fore.parentId)
        assertEquals(upper.tail, fore.head)
        assertEquals(fore.id, state.strokes.single().boneId)
    }

    @Test
    fun turningTheUpperArmCarriesTheForearmAndItsInkAndUndoes() {
        val state = arm()
        state.posingBones = true
        // Swing the upper arm's tail from (100,0) down to (0,100): a quarter turn about the shoulder.
        state.beginBone(WorldPoint(100.0, 0.0))
        state.continueBone(WorldPoint(70.0, 70.0))
        state.continueBone(WorldPoint(0.0, 100.0))
        state.endBone()
        val fore = state.bones[1]
        assertEquals(0.0, fore.tail.x, 1e-6)
        assertEquals(200.0, fore.tail.y, 1e-6)
        val ink = state.strokes.single().points
        assertEquals(0.0, ink[0].x, 1e-6)
        assertEquals(150.0, ink[0].y, 1e-6)
        state.undo()
        assertEquals(WorldPoint(200.0, 0.0), state.bones[1].tail)
        assertEquals(150.0, state.strokes.single().points[0].x, 1e-9)
    }

    @Test
    fun copiedFramesCarryThePoseAndInBetweensTweenIt() {
        val state = arm()
        state.insertAnimFrame(copyCurrent = true)
        assertEquals(4, state.bones.size)
        assertEquals(setOf(0, 1), state.bones.map { it.animFrame }.toSet())
        // Pose frame 1: the forearm straight down.
        state.posingBones = true
        state.beginBone(WorldPoint(200.0, 0.0))
        state.continueBone(WorldPoint(100.0, 100.0))
        state.endBone()
        state.goToAnimFrame(0)
        state.generateInBetweens(1)
        val mid = state.bones.filter { it.animFrame == 1 }.associateBy { it.parentId != null }
        val fore = mid.getValue(true)
        assertEquals(mid.getValue(false).tail, fore.head)
        assertEquals(Math.PI / 4, fore.angle, 1e-6) // halfway from level to a quarter turn down
        assertEquals(setOf(0, 1, 2), state.bones.map { it.animFrame }.toSet())
    }

    /** Roadmap item 84: a line along the whole arm, bound to the forearm, bends at the elbow. */
    private fun bendElbow(smooth: Boolean): List<WorldPoint> {
        val state = arm()
        val line = Stroke(Color.Black, 2.0).onBone(state.bones[1].id).apply {
            addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)); addPoint(WorldPoint(200.0, 0.0))
        }
        state.loadStrokes(listOf(line))
        state.posingBones = true
        state.smoothJoints = smooth
        // A quarter turn of the forearm about the elbow (100,0).
        state.beginBone(WorldPoint(200.0, 0.0))
        state.continueBone(WorldPoint(100.0, 100.0))
        state.endBone()
        return state.strokes.single().points
    }

    @Test
    fun smoothJointsBendInkByDistanceWhileRigidMovesItWhole() {
        val smooth = bendElbow(smooth = true)
        assertEquals(WorldPoint(0.0, 0.0), smooth[0]) // far up the upper arm: weight 0, unmoved
        assertEquals(WorldPoint(100.0, 0.0), smooth[1]) // on the joint itself
        assertEquals(100.0, smooth[2].x, 1e-6) // the hand: weight 1, turned fully
        assertEquals(100.0, smooth[2].y, 1e-6)
        val rigid = bendElbow(smooth = false)
        assertEquals(100.0, rigid[0].x, 1e-6) // the whole line turned about the elbow
        assertEquals(-100.0, rigid[0].y, 1e-6)
    }

    @Test
    fun deletingABoneTakesItsChildrenAndUnbindsTheirInk() {
        val state = arm()
        state.selectedBoneId = state.bones[0].id
        state.deleteSelectedBone()
        assertTrue(state.bones.isEmpty())
        assertNull(state.strokes.single().boneId)
    }

    @Test
    fun bonesAndBindingsAreSavedAndTravelInBundles() {
        val state = arm()
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), state.strokes.toList()) }.toByteArray()
        assertEquals(state.bones[1].id, StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes))).single().boneId)
        val doc = CanvasStorage.createDocument(context, "Puppet")
        CanvasStorage.saveBones(context, doc.id, state.bones.toList())
        assertEquals(state.bones.toList(), CanvasStorage.loadBones(context, doc.id))
        val imported = importDocumentBundle(context, exportDocumentBundle(context, doc.id, "Puppet").inputStream())!!
        assertEquals(2, CanvasStorage.loadBones(context, imported.id).size)
    }
}
