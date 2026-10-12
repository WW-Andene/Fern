package com.andene.fern.canvas

import kotlin.math.abs
import kotlin.math.hypot

/**
 * One bone's timeline for the dopesheet and curve editor (roadmap item 134, Spine's dopesheet and
 * graph): for each animation frame, the bone's angle - relative to its parent's, the rotation an
 * animator keys, or its own for a root - and whether its pose changes there from the frame
 * before (a key) or holds. Null where the bone isn't in that frame.
 */
data class BoneTrack(val boneId: String, val label: String, val angles: List<Double?>, val keys: List<Boolean>)

/** How far apart (world units) two poses of a bone must be to count as a change. */
private const val POSE_EPSILON = 1e-6

/**
 * The tracks of every bone in [bones] across [frameCount] frames, in the order bones were first
 * created (each frame lists its bones in that order). Bones are labelled by that order ("Bone 1"
 * via [label]), since bones have no names.
 */
fun boneTracks(bones: List<Bone>, frameCount: Int, label: (Int) -> String): List<BoneTrack> {
    val byFrame = bones.groupBy { it.animFrame }
    val ids = bones.map { it.id }.distinct()
    return ids.mapIndexed { index, id ->
        val poses = List(frameCount) { frame -> byFrame[frame]?.firstOrNull { it.id == id } }
        val angles = List(frameCount) { frame ->
            val bone = poses[frame] ?: return@List null
            val parent = bone.parentId?.let { pid -> byFrame[frame]?.firstOrNull { it.id == pid } }
            if (parent != null) relativeAngle(bone, parent) else bone.angle
        }
        val keys = List(frameCount) { frame ->
            val bone = poses[frame] ?: return@List false
            val before = poses.getOrNull(frame - 1) ?: return@List true
            hypot(bone.head.x - before.head.x, bone.head.y - before.head.y) > POSE_EPSILON ||
                hypot(bone.tail.x - before.tail.x, bone.tail.y - before.tail.y) > POSE_EPSILON ||
                abs((angles[frame] ?: 0.0) - (angles[frame - 1] ?: 0.0)) > POSE_EPSILON
        }
        BoneTrack(id, label(index + 1), angles, keys)
    }
}
