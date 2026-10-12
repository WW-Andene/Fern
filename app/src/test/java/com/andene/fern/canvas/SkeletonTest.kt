package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

/** Roadmap item 83: bone geometry - subtrees, hit-testing and pose interpolation. */
class SkeletonTest {
    private val upper = Bone("upper", null, WorldPoint(0.0, 0.0), WorldPoint(10.0, 0.0))
    private val lower = Bone("lower", "upper", WorldPoint(10.0, 0.0), WorldPoint(20.0, 0.0))
    private val other = Bone("other", null, WorldPoint(0.0, 50.0), WorldPoint(10.0, 50.0))

    @Test
    fun subtreeFollowsParentLinks() {
        assertEquals(setOf("upper", "lower"), boneSubtree(listOf(upper, lower, other), "upper"))
        assertEquals(setOf("lower"), boneSubtree(listOf(upper, lower, other), "lower"))
    }

    @Test
    fun hitTestingPicksTheNearestBoneInReach() {
        assertEquals("lower", boneAt(listOf(upper, lower, other), WorldPoint(15.0, 2.0), 4.0)?.id)
        assertNull(boneAt(listOf(upper, lower, other), WorldPoint(15.0, 20.0), 4.0))
    }

    @Test
    fun halfwayPoseSwingsLimbsAndKeepsTheChainJoined() {
        // The whole arm turns a quarter turn about the shoulder.
        val raisedUpper = Bone("upper", null, WorldPoint(0.0, 0.0), WorldPoint(0.0, -10.0), 1)
        val raisedLower = Bone("lower", "upper", WorldPoint(0.0, -10.0), WorldPoint(0.0, -20.0), 1)
        val mid = interpolatePose(listOf(upper, lower), listOf(raisedUpper, raisedLower), 0.5, 0).associateBy { it.id }
        val u = mid.getValue("upper")
        val l = mid.getValue("lower")
        assertEquals(10.0, u.length, 1e-9) // swung, not shortened
        assertEquals(-PI / 4, u.angle, 1e-9)
        assertEquals(0.0, hypot(l.head.x - u.tail.x, l.head.y - u.tail.y), 1e-9) // still joined
    }

    @Test
    fun rotateAboutTurnsAroundTheCentre() {
        val p = rotateAbout(WorldPoint(2.0, 1.0), WorldPoint(1.0, 1.0), PI / 2)
        assertEquals(1.0, p.x, 1e-12)
        assertEquals(2.0, p.y, 1e-12)
    }
}
