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
    fun paintedWeightsOverrideTheAutomaticOnesAndSurviveSaving() {
        val state = arm()
        val line = Stroke(Color.Black, 2.0).onBone(state.bones[1].id).apply {
            addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)); addPoint(WorldPoint(200.0, 0.0))
        }
        state.loadStrokes(listOf(line))
        state.selectedBoneId = state.bones[1].id
        state.paintingWeights = true
        state.weightPaintValue = 1.0
        // Paint the far end of the upper arm: it now follows the forearm fully.
        state.beginBone(WorldPoint(0.0, 0.0))
        state.endBone()
        assertEquals(1f, line.jointWeights!![0])
        assertTrue(line.jointWeights!![2].isNaN())
        // The weights travel through the stroke format.
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), listOf(line)) }.toByteArray()
        assertEquals(1f, StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes))).single().jointWeights!![0])
        state.paintingWeights = false
        state.posingBones = true
        state.beginBone(WorldPoint(200.0, 0.0))
        state.continueBone(WorldPoint(100.0, 100.0))
        state.endBone()
        val bent = state.strokes.single().points
        assertEquals(100.0, bent[0].x, 1e-6) // painted 1: turned with the forearm about the elbow
        assertEquals(-100.0, bent[0].y, 1e-6)
        assertEquals(100.0, bent[2].y, 1e-6) // still automatic: the hand turns fully
    }

    @Test
    fun aLimitedElbowStopsAtItsLimitWhenTurnedOrReached() {
        val state = arm()
        val fore = state.bones[1]
        // The elbow may bend 0..45 degrees (downwards on screen, y pointing down).
        state.setJointLimits(fore.id, 0.0, Math.PI / 4)
        state.posingBones = true
        state.beginBone(WorldPoint(200.0, 0.0))
        state.continueBone(WorldPoint(100.0, 100.0)) // asks for 90 degrees
        state.endBone()
        assertEquals(Math.PI / 4, relativeAngle(state.bones[1], state.bones[0]), 1e-6)
        state.beginBone(state.bones[1].tail)
        state.continueBone(WorldPoint(100.0, -100.0)) // asks to bend backwards
        state.endBone()
        assertEquals(0.0, relativeAngle(state.bones[1], state.bones[0]), 1e-6)
        state.inverseKinematics = true
        state.beginBone(state.bones[1].tail)
        state.continueBone(WorldPoint(60.0, -60.0)) // reachable only by bending backwards
        state.endBone()
        val bend = relativeAngle(state.bones[1], state.bones[0])
        assertTrue(bend >= -1e-6 && bend <= Math.PI / 4 + 1e-6)
    }

    @Test
    fun aDynamicBoneTrailsItsParentThenSettles() {
        val state = arm()
        val upper = state.bones[0]
        val fore = state.bones[1]
        state.setBoneDynamic(fore.id, true)
        // Frame 0 at rest; frame 1 the arm swung a quarter turn; then held for several frames.
        state.insertAnimFrame(copyCurrent = true)
        state.posingBones = true
        state.beginBone(WorldPoint(100.0, 0.0))
        state.continueBone(WorldPoint(0.0, 100.0))
        state.endBone()
        repeat(8) { state.insertAnimFrame(copyCurrent = true) }
        state.simulateDynamics()
        fun bend(frame: Int): Double {
            val b = state.bones.filter { it.animFrame == frame }.associateBy { it.id }
            return relativeAngle(b.getValue(fore.id), b.getValue(upper.id))
        }
        assertEquals(0.0, bend(0), 1e-9) // the first frame is the rest pose
        assertTrue(kotlin.math.abs(bend(1)) > 0.1) // the forearm lags behind the swing
        assertTrue(kotlin.math.abs(bend(9)) < kotlin.math.abs(bend(1))) // and settles back
        state.undo()
        assertEquals(0.0, bend(1), 1e-9)
    }

    @Test
    fun blendingAPoseMixesItWithAnotherFrameAndCarriesTheInk() {
        val state = arm()
        state.insertAnimFrame(copyCurrent = true)
        state.posingBones = true
        state.beginBone(WorldPoint(200.0, 0.0))
        state.continueBone(WorldPoint(100.0, 100.0)) // frame 1: forearm a quarter turn down
        state.endBone()
        state.goToAnimFrame(0)
        state.blendPoseWith(1, 0.5)
        val fore = state.bones.first { it.animFrame == 0 && it.parentId != null }
        assertEquals(Math.PI / 4, fore.angle, 1e-6)
        // The forearm's ink turned with it: (150,0) is now 50 along the 45-degree forearm.
        val ink = state.strokes.first { it.animFrame == 0 }.points.first()
        assertEquals(100 + 50 / Math.sqrt(2.0), ink.x, 1e-6)
        assertEquals(50 / Math.sqrt(2.0), ink.y, 1e-6)
        state.undo()
        assertEquals(0.0, state.bones.first { it.animFrame == 0 && it.parentId != null }.angle, 1e-9)
    }

    @Test
    fun choosingASkinShowsItsLayersAndHidesTheOthers() {
        val state = CanvasState()
        state.loadLayers(
            listOf(
                Layer("body", "Body"),
                Layer("red", "Red coat", skin = "Red"),
                Layer("blue", "Blue coat", visible = false, skin = "Blue"),
            ),
        )
        assertEquals(listOf("Red", "Blue"), state.skins)
        assertEquals("Red", state.activeSkin)
        state.applySkin("Blue")
        assertEquals(listOf(true, false, true), state.layers.map { it.visible })
        assertEquals("Blue", state.activeSkin)
        val doc = CanvasStorage.createDocument(context, "skins")
        CanvasStorage.saveLayers(context, doc.id, state.layers.toList())
        assertEquals(state.layers.toList(), CanvasStorage.loadLayers(context, doc.id))
    }

    @Test
    fun jointLimitsAndDynamicsAreSaved() {
        val state = arm()
        val doc = CanvasStorage.createDocument(context, "limits")
        val limited = state.bones.map { if (it.parentId != null) it.copy(minTurn = -0.5, maxTurn = 1.0, dynamic = true) else it }
        CanvasStorage.saveBones(context, doc.id, limited)
        assertEquals(limited, CanvasStorage.loadBones(context, doc.id))
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
    fun inverseKinematicsPullsTheChainSoTheHandReachesTheFinger() {
        val state = arm()
        state.posingBones = true
        state.inverseKinematics = true
        val target = WorldPoint(120.0, 80.0) // in reach (arm length 200), so the elbow must bend
        state.beginBone(WorldPoint(200.0, 0.0))
        state.continueBone(WorldPoint(170.0, 40.0))
        state.continueBone(target)
        state.endBone()
        val (upper, fore) = state.bones
        assertEquals(WorldPoint(0.0, 0.0), upper.head) // the shoulder stays put
        assertEquals(0.0, kotlin.math.hypot(fore.head.x - upper.tail.x, fore.head.y - upper.tail.y), 1e-6) // still joined
        assertEquals(100.0, upper.length, 1e-6)
        assertTrue(kotlin.math.hypot(fore.tail.x - target.x, fore.tail.y - target.y) < 1.5)
        // The forearm's ink went with the forearm.
        assertTrue(distanceToBone(fore, state.strokes.single().points.last()) < 1e-6)
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
