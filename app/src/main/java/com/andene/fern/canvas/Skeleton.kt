package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One bone of a character's skeleton (roadmap item 83): a segment from [head], the joint it
 * turns about, to [tail], where child bones attach. [parentId] names the bone it hangs from
 * (null for a root). Bones belong to an animation frame like strokes do: each frame holds its
 * own pose, a copied frame copies the pose, and a bone keeps its [id] across frames so poses
 * can be matched for in-betweens. Strokes bound to a bone ([Stroke.boneId]) move with it.
 */
data class Bone(
    val id: String,
    val parentId: String?,
    val head: WorldPoint,
    val tail: WorldPoint,
    val animFrame: Int = 0,
    /**
     * Joint limits (roadmap item 85): the range, in radians, this bone's angle may take relative
     * to its parent's (see [relativeAngle]); null when the joint turns freely. Inverse kinematics
     * keeps to it, like an elbow that doesn't bend backwards.
     */
    val minTurn: Double? = null,
    val maxTurn: Double? = null,
    /** Follows its parent with springy lag and overshoot when dynamics are simulated ([simulateDynamics]). */
    val dynamic: Boolean = false,
) {
    val length: Double get() = hypot(tail.x - head.x, tail.y - head.y)
    val angle: Double get() = atan2(tail.y - head.y, tail.x - head.x)

    fun shiftedBy(offset: WorldPoint) = copy(head = head - offset, tail = tail - offset)
}

/** [point] turned by [angle] radians about [center]. */
fun rotateAbout(point: WorldPoint, center: WorldPoint, angle: Double): WorldPoint {
    val c = cos(angle)
    val s = sin(angle)
    val dx = point.x - center.x
    val dy = point.y - center.y
    return WorldPoint(center.x + dx * c - dy * s, center.y + dx * s + dy * c)
}

/** Ids of the bone [rootId] and every bone hanging from it, among [bones]. */
fun boneSubtree(bones: List<Bone>, rootId: String): Set<String> {
    val result = linkedSetOf(rootId)
    var grew = true
    while (grew) {
        grew = false
        for (bone in bones) if (bone.parentId in result && result.add(bone.id)) grew = true
    }
    return result
}

/** Distance from [point] to the bone's segment. */
fun distanceToBone(bone: Bone, point: WorldPoint): Double = distanceToPolyline(listOf(bone.head, bone.tail), point)

/** The bone nearest [point] within [tolerance], or null. */
fun boneAt(bones: List<Bone>, point: WorldPoint, tolerance: Double): Bone? =
    bones.filter { distanceToBone(it, point) <= tolerance }.minByOrNull { distanceToBone(it, point) }

/**
 * The pose between [from] and [to] at progress [t]: bones are matched by id and each turned the
 * short way round while its length changes evenly, so limbs swing instead of shrinking through
 * the middle. A root's joint moves straight from one position to the other; a bone attached at
 * its parent's tail stays attached (its joint is the interpolated parent's tail), so the chain
 * never comes apart mid-tween. A bone in only one of the poses is left out. The result is placed
 * on [animFrame], parents before children.
 */
fun interpolatePose(from: List<Bone>, to: List<Bone>, t: Double, animFrame: Int): List<Bone> {
    val targets = to.associateBy { it.id }
    val sources = from.associateBy { it.id }
    val done = LinkedHashMap<String, Bone>()
    val visiting = HashSet<String>() // a parent loop (only from a damaged file) is treated as unattached
    fun place(a: Bone): Bone? {
        done[a.id]?.let { return it }
        val b = targets[a.id] ?: return null
        if (!visiting.add(a.id)) return null
        val parentA = a.parentId?.let { sources[it] }
        val attached = parentA != null && isAttached(a, parentA) && targets[parentA.id]?.let { isAttached(b, it) } == true
        val head = (if (attached) place(parentA!!)?.tail else null)
            ?: WorldPoint(a.head.x + (b.head.x - a.head.x) * t, a.head.y + (b.head.y - a.head.y) * t)
        var turn = b.angle - a.angle
        while (turn > PI) turn -= 2 * PI
        while (turn < -PI) turn += 2 * PI
        val angle = a.angle + turn * t
        val length = a.length + (b.length - a.length) * t
        return a.copy(head = head, tail = WorldPoint(head.x + cos(angle) * length, head.y + sin(angle) * length), animFrame = animFrame).also { done[a.id] = it }
    }
    from.forEach { place(it) }
    return done.values.toList()
}

/** True if [child]'s joint sits on [parent]'s tail (chained, rather than placed apart). */
fun isAttached(child: Bone, parent: Bone): Boolean =
    hypot(child.head.x - parent.tail.x, child.head.y - parent.tail.y) <= 1e-6 * maxOf(1.0, parent.length)

/** [angle] wrapped into -pi..pi. */
fun normalizeAngle(angle: Double): Double {
    var a = angle % (2 * PI)
    if (a > PI) a -= 2 * PI
    if (a < -PI) a += 2 * PI
    return a
}

/** [bone]'s angle relative to [parent]'s, in -pi..pi. */
fun relativeAngle(bone: Bone, parent: Bone): Double = normalizeAngle(bone.angle - parent.angle)

/**
 * The part of a turn by [turn] that [bone]'s joint limits allow, given its [parent]: the turn
 * that brings the relative angle as close to the wanted one as the range permits. A bone with
 * no limits, or no parent, turns freely.
 */
fun limitedTurn(bone: Bone, parent: Bone?, turn: Double): Double {
    val min = bone.minTurn
    val max = bone.maxTurn
    if (parent == null || min == null || max == null) return turn
    val current = relativeAngle(bone, parent)
    val wanted = normalizeAngle(current + turn)
    return wanted.coerceIn(min, max) - current
}


/**
 * A bone being turned, applied from its start on every move so posing never drifts: the turned
 * bone's joint and the pointer's starting angle about it, the bones that move with it, the
 * frame's bones when it started, and each moved stroke's starting points with per-point weights
 * (null = rigid; see [CanvasState.smoothJoints]).
 */
class BoneTurn internal constructor(
    val center: WorldPoint,
    val startAngle: Double,
    val moved: Set<String>,
    val startBones: List<Bone>,
    val strokes: List<Triple<Stroke, List<WorldPoint>, DoubleArray?>>,
)
