package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer

/** Roadmap item 146: the posable 3D hand, the 3D head, and the hand pose scanner's landmark reading. */
@RunWith(RobolectricTestRunner::class)
class HandsAndHeadsTest {
    private fun topOf(mesh: Mesh) = (0 until mesh.vertexCount).maxOf { mesh.positions[3 * it + 1] }

    @Test
    fun aFistIsShorterThanAnOpenHandAndTheLeftHandMirrorsTheRight() {
        val open = handMesh(HandPreset.OPEN.pose)
        val fist = handMesh(HandPreset.FIST.pose)
        assertEquals(open.vertexCount, fist.vertexCount) // same parts, so painting carries over between poses
        assertTrue(topOf(open) > topOf(fist) + 0.3f)
        // Fingers curl toward the palm side (+z).
        assertTrue((0 until fist.vertexCount).maxOf { fist.positions[3 * it + 2] } > (0 until open.vertexCount).maxOf { open.positions[3 * it + 2] })
        val right = handMesh(HandPose())
        val left = handMesh(HandPose(left = true))
        for (i in 0 until right.vertexCount) {
            assertEquals(-right.positions[3 * i], left.positions[3 * i], 1e-5f)
            assertEquals(right.positions[3 * i + 1], left.positions[3 * i + 1], 1e-5f)
        }
        assertTrue(right.uvs.all { it in 0f..1f })
    }

    @Test
    fun theHeadHasANoseAndGuideLines() {
        val head = headMesh()
        // The furthest point forward is the nose, on the centre line below the eyes.
        val front = (0 until head.vertexCount).maxBy { head.positions[3 * it + 2] }
        assertEquals(0f, head.positions[3 * front], 0.1f)
        assertTrue(head.positions[3 * front + 1] < 0f)
        val maps = headMaps(256)
        assertTrue(maps.color[128 * 256 + 64] != maps.color[100 * 256 + 100]) // the eye line differs from the skin
    }

    @Test
    fun modelFilesKeepTheHandPoseAndStillReadVersionOne() {
        val pose = HandPreset.PEACE.pose.copy(left = true)
        val model = Model3d(handMesh(pose), SurfaceMaps.blank(8, 0), handPose = pose)
        assertEquals(pose, Model3dFile.read(Model3dFile.write(model)).handPose)
        // A version 1 file has nothing after the light: patch a pose-less file's version back to 1.
        val bytes = Model3dFile.write(Model3d(primitiveMesh(MeshPrimitive.CUBE), SurfaceMaps.blank(8, 0)))
        ByteBuffer.wrap(bytes).putInt(4, 1)
        assertNull(Model3dFile.read(bytes).handPose)
    }

    /** Landmarks of a flat right hand in the xy plane, fingers up, each finger's joints optionally bent toward +z. */
    private fun landmarks(bentFinger: Finger? = null, thumbAcross: Boolean = false): List<Vec3> {
        val points = MutableList(21) { Vec3(0.0, 0.0, 0.0) }
        val bases = mapOf(Finger.INDEX to -0.03, Finger.MIDDLE to -0.01, Finger.RING to 0.01, Finger.LITTLE to 0.03)
        for ((finger, x) in bases) {
            val start = 1 + finger.ordinal * 4
            for (k in 0..3) {
                points[start + k] = if (finger == bentFinger && k > 0) {
                    // Each segment turns a further 80 degrees toward the palm.
                    var p = Vec3(x, 0.08, 0.0)
                    for (j in 1..k) p += Vec3(0.0, 0.025 * kotlin.math.cos(Math.toRadians(80.0 * j)), 0.025 * kotlin.math.sin(Math.toRadians(80.0 * j)))
                    p
                } else {
                    Vec3(x, 0.08 + 0.025 * k, 0.0)
                }
            }
        }
        // Thumb: out to the side, or its tip at the little finger's knuckle.
        for (k in 0..3) points[1 + k] = Vec3(-0.035 - 0.015 * k, 0.02 + 0.015 * k, 0.0)
        if (thumbAcross) points[4] = Vec3(0.028, 0.08, 0.005)
        return points
    }

    @Test
    fun landmarksBecomeCurlsSpreadsAndThumbReach() {
        val flat = landmarksToPose(landmarks(), left = false)
        for (finger in listOf(Finger.INDEX, Finger.MIDDLE, Finger.RING, Finger.LITTLE)) assertEquals(0.0, flat.curl(finger), 0.05)
        assertTrue(flat.thumbAcross < 0.3)
        val bent = landmarksToPose(landmarks(bentFinger = Finger.INDEX), left = true)
        assertTrue(bent.curl(Finger.INDEX) > 0.7)
        assertEquals(0.0, bent.curl(Finger.RING), 0.05)
        assertTrue(bent.left)
        assertTrue(landmarksToPose(landmarks(thumbAcross = true), left = false).thumbAcross > 0.8)
    }
}
