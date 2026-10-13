package com.andene.fern.canvas

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The rig model (roadmap items 250-253), built the way Spine's is: bones carry a local transform
 * relative to their parent (position, rotation, scale and shear) in a setup pose; animations are
 * keyed timelines per property layered on the setup pose; slots hang from bones and show an
 * attachment - the artist's own drawing - whose points follow one or several bones by weight.
 * Posing never rewrites the drawing: the posed geometry is computed from the setup drawing, the
 * setup pose and the animation at a given time.
 */

/** A 2D affine transform: x' = a·x + c·y + tx, y' = b·x + d·y + ty. */
data class Affine(val a: Double, val b: Double, val c: Double, val d: Double, val tx: Double, val ty: Double) {
    operator fun times(o: Affine) = Affine(
        a * o.a + c * o.b,
        b * o.a + d * o.b,
        a * o.c + c * o.d,
        b * o.c + d * o.d,
        a * o.tx + c * o.ty + tx,
        b * o.tx + d * o.ty + ty,
    )

    fun apply(p: WorldPoint) = WorldPoint(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)

    fun inverse(): Affine {
        val det = a * d - b * c
        if (kotlin.math.abs(det) < 1e-300) return IDENTITY
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return Affine(ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty))
    }

    /** The world angle of this transform's x axis, radians. */
    val angle: Double get() = atan2(b, a)

    companion object {
        val IDENTITY = Affine(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

        /**
         * The local transform of a bone: scale and shear applied in the bone's own frame, then its
         * rotation, then its position - Spine's order (shear angles in radians, applied per axis).
         */
        fun local(x: Double, y: Double, rotation: Double, scaleX: Double, scaleY: Double, shearX: Double, shearY: Double): Affine {
            val rx = rotation + shearX
            val ry = rotation + Math.PI / 2 + shearY
            return Affine(cos(rx) * scaleX, sin(rx) * scaleX, cos(ry) * scaleY, sin(ry) * scaleY, x, y)
        }
    }
}

/** What a bone takes from its parent (Spine's "inherit"). */
enum class BoneInherit { NORMAL, ONLY_TRANSLATION, NO_ROTATION_OR_REFLECTION, NO_SCALE }

/**
 * A bone in the setup pose: its transform relative to its parent (or to the world for a root),
 * and its length along its own x axis. Angles are radians.
 */
data class RigBone(
    val id: String,
    val name: String,
    val parentId: String?,
    val x: Double,
    val y: Double,
    val rotation: Double,
    val length: Double,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val shearX: Double = 0.0,
    val shearY: Double = 0.0,
    val inherit: BoneInherit = BoneInherit.NORMAL,
    /** Follow-through (roadmap item 292): the bone lags behind and overshoots its animated angle. */
    val physics: BonePhysics? = null,
)

/**
 * A physics constraint on one bone (roadmap item 292), as Spine's: the bone's world angle is a
 * damped spring pulled towards where the animation puts it. [strength] is how hard it is pulled
 * back (0 loose, 1 stiff), [damping] how quickly the swing dies (0 swings on, 1 settles at once).
 */
data class BonePhysics(val strength: Double = 0.3, val damping: Double = 0.2)

/** The bone properties an animation can key. */
enum class BoneProperty { ROTATE, X, Y, SCALE_X, SCALE_Y, SHEAR_X, SHEAR_Y }

/** How the value moves from a key to the next one. */
sealed class KeyCurve {
    data object Linear : KeyCurve()

    /** Holds the key's value until the next key. */
    data object Stepped : KeyCurve()

    /** A timing curve from this key to the next (handles in 0..1 of the span, as the graph editor draws them). */
    data class Bezier(val curve: CubicEasing) : KeyCurve()
}

/** A key: [value] at [time] (in frames, fractional allowed), and the curve to the next key. */
data class Key(val time: Double, val value: Double, val curve: KeyCurve = KeyCurve.Linear)

/**
 * One property of one bone over time. A ROTATE or SHEAR value is added to the setup value
 * (radians); X and Y are added offsets; SCALE values multiply the setup scale - Spine's convention.
 */
data class BoneTimeline(val boneId: String, val property: BoneProperty, val keys: List<Key>) {
    /** The keyed value at [time]: held before the first key and after the last, curved between. */
    fun valueAt(time: Double): Double? {
        if (keys.isEmpty()) return null
        if (time <= keys.first().time) return keys.first().value
        if (time >= keys.last().time) return keys.last().value
        val next = keys.indexOfFirst { it.time > time }
        val from = keys[next - 1]
        val to = keys[next]
        val t = (time - from.time) / (to.time - from.time)
        val eased = when (val curve = from.curve) {
            KeyCurve.Linear -> t
            KeyCurve.Stepped -> 0.0
            is KeyCurve.Bezier -> curve.curve.at(t)
        }
        return from.value + (to.value - from.value) * eased
    }
}

/** A named animation: its length in frames and its timelines. */
data class RigAnimation(val id: String, val name: String, val length: Int, val timelines: List<BoneTimeline>)

/** A slot: a place on a bone that shows one attachment at a time, in draw order (roadmap item 272). */
data class RigSlot(val id: String, val name: String, val boneId: String, val attachmentId: String?)

/**
 * An attachment (roadmap items 272-273, 353): the artist's own ink, kept as drawn in the setup
 * pose. [weights] gives, per stroke and per point, the bones that point follows and how much
 * (summing to 1); a point with a single bone moves rigidly with it.
 */
data class RigAttachment(
    val id: String,
    val name: String,
    val strokes: List<Stroke>,
    val weights: List<List<List<Pair<String, Double>>>>,
)

/** A whole rig: bones (parents before children), slots in draw order, attachments and animations. */
data class Rig(
    val bones: List<RigBone>,
    val slots: List<RigSlot> = emptyList(),
    val attachments: List<RigAttachment> = emptyList(),
    val animations: List<RigAnimation> = emptyList(),
) {
    /** Each bone's world transform in the setup pose. */
    fun setupPose(): Map<String, Affine> = pose(null, 0.0)

    /**
     * Each bone's world transform in [animation] at [time] (the setup pose when [animation] is
     * null). [angles] replaces the world angle of the bones it names, keeping their origin and
     * scale, before their children are placed: how physics bones lag (see [simulatePhysics]).
     */
    fun pose(animation: RigAnimation?, time: Double, angles: Map<String, Double> = emptyMap()): Map<String, Affine> {
        val byBone = animation?.timelines?.groupBy { it.boneId }.orEmpty()
        val world = HashMap<String, Affine>(bones.size)
        for (bone in bones) {
            val keyed = byBone[bone.id].orEmpty()
            fun keyedValue(p: BoneProperty) = keyed.firstOrNull { it.property == p }?.valueAt(time)
            val local = Affine.local(
                bone.x + (keyedValue(BoneProperty.X) ?: 0.0),
                bone.y + (keyedValue(BoneProperty.Y) ?: 0.0),
                bone.rotation + (keyedValue(BoneProperty.ROTATE) ?: 0.0),
                bone.scaleX * (keyedValue(BoneProperty.SCALE_X) ?: 1.0),
                bone.scaleY * (keyedValue(BoneProperty.SCALE_Y) ?: 1.0),
                bone.shearX + (keyedValue(BoneProperty.SHEAR_X) ?: 0.0),
                bone.shearY + (keyedValue(BoneProperty.SHEAR_Y) ?: 0.0),
            )
            val parent = bone.parentId?.let { world[it] }
            val placed = if (parent == null) local else inherited(parent, bone.inherit) * local
            world[bone.id] = angles[bone.id]?.let { withAngle(placed, it) } ?: placed
        }
        return world
    }

    /**
     * The world angles of the physics bones at each frame from 0 to [frame] of [animation]
     * (roadmap item 292): each starts on its animated angle and then follows it as a damped
     * spring, stepped [PHYSICS_SUBSTEPS] times a frame, so swings carry over from frame to frame.
     * Parents are settled before children, so a tail's tip lags behind its lagging base.
     */
    fun simulatePhysics(animation: RigAnimation?, frame: Int): Map<String, Double> {
        val physical = bones.filter { it.physics != null }
        if (physical.isEmpty() || animation == null) return emptyMap()
        val angle = HashMap<String, Double>()
        val speed = HashMap<String, Double>()
        for (f in 0..frame) {
            for (step in 1..PHYSICS_SUBSTEPS) {
                val t = f - 1 + step.toDouble() / PHYSICS_SUBSTEPS
                if (f == 0 && step < PHYSICS_SUBSTEPS) continue
                for (bone in physical) {
                    // The angle the animation and the (already lagging) parents would give it.
                    val unconstrained = pose(animation, t.coerceAtLeast(0.0), angle - bone.id)[bone.id]?.angle ?: continue
                    val p = bone.physics ?: continue
                    val a = angle[bone.id]
                    if (a == null) {
                        angle[bone.id] = unconstrained
                        speed[bone.id] = 0.0
                        continue
                    }
                    val dt = 1.0 / PHYSICS_SUBSTEPS
                    val pull = normalizeAngle(unconstrained - a)
                    val v = (speed[bone.id] ?: 0.0) * (1 - p.damping.coerceIn(0.0, 1.0)) + pull * p.strength.coerceIn(0.0, 1.0) * PHYSICS_STIFFNESS * dt
                    speed[bone.id] = v
                    angle[bone.id] = normalizeAngle(a + v * dt)
                }
            }
        }
        return angle
    }

    /** [world] turned to the world [angle], keeping its origin and its axes' lengths and shear. */
    private fun withAngle(world: Affine, angle: Double): Affine {
        val turn = normalizeAngle(angle - world.angle)
        val c = cos(turn)
        val s = sin(turn)
        return Affine(c * world.a - s * world.b, s * world.a + c * world.b, c * world.c - s * world.d, s * world.c + c * world.d, world.tx, world.ty)
    }

    /**
     * The posed points of [attachment]: each point carried from the setup pose to [pose] by its
     * bones' movements, blended by weight (linear blend skinning, as Spine's weighted meshes).
     */
    fun deform(attachment: RigAttachment, pose: Map<String, Affine>, setup: Map<String, Affine> = setupPose()): List<List<WorldPoint>> {
        val moves = HashMap<String, Affine>()
        fun move(boneId: String) = moves.getOrPut(boneId) { (pose[boneId] ?: Affine.IDENTITY) * (setup[boneId] ?: Affine.IDENTITY).inverse() }
        return attachment.strokes.mapIndexed { s, stroke ->
            val strokeWeights = attachment.weights.getOrNull(s)
            stroke.points.mapIndexed { i, p ->
                val w = strokeWeights?.getOrNull(i)
                if (w.isNullOrEmpty()) p else {
                    var x = 0.0
                    var y = 0.0
                    for ((boneId, weight) in w) {
                        val q = move(boneId).apply(p)
                        x += q.x * weight
                        y += q.y * weight
                    }
                    WorldPoint(x, y)
                }
            }
        }
    }

    private fun inherited(parent: Affine, inherit: BoneInherit): Affine = when (inherit) {
        BoneInherit.NORMAL -> parent
        BoneInherit.ONLY_TRANSLATION -> Affine(1.0, 0.0, 0.0, 1.0, parent.tx, parent.ty)
        BoneInherit.NO_ROTATION_OR_REFLECTION -> {
            val sx = hypot(parent.a, parent.b)
            val sy = hypot(parent.c, parent.d)
            Affine(sx, 0.0, 0.0, sy, parent.tx, parent.ty)
        }
        BoneInherit.NO_SCALE -> {
            val angle = parent.angle
            Affine(cos(angle), sin(angle), -sin(angle), cos(angle), parent.tx, parent.ty)
        }
    }
}

/** The world position of [bone]'s tip in a pose: its origin moved [RigBone.length] along its x axis. */
fun boneTip(bone: RigBone, world: Affine): WorldPoint = world.apply(WorldPoint(bone.length, 0.0))

/** The world position of a bone's origin in a pose. */
fun boneOrigin(world: Affine): WorldPoint = WorldPoint(world.tx, world.ty)

/**
 * Automatic weights (roadmap item 285, first step): each point follows the bones nearest it,
 * inverse-square by distance to the bone's segment, keeping the [maxBones] strongest and
 * normalising to 1. Distance across the drawing's own shape (topology), as Spine computes, is
 * the next step; distance alone already lets one outline bend across a joint.
 */
fun autoWeights(points: List<WorldPoint>, bones: List<RigBone>, setup: Map<String, Affine>, maxBones: Int = 2): List<List<Pair<String, Double>>> {
    val segments = bones.mapNotNull { b -> setup[b.id]?.let { w -> Triple(b.id, boneOrigin(w), boneTip(b, w)) } }
    return points.map { p ->
        val near = segments.map { (id, a, b) -> id to distanceToPolyline(listOf(a, b), p) }.sortedBy { it.second }.take(maxBones)
        if (near.isEmpty()) return@map emptyList()
        if (near.first().second < 1e-9) return@map listOf(near.first().first to 1.0)
        val raw = near.map { (id, d) -> id to 1.0 / (d * d) }
        val total = raw.sumOf { it.second }
        raw.map { (id, w) -> id to w / total }
    }
}

// Physics steps per frame, and how strongly strength 1 pulls: lively at 12 fps without blowing up.
private const val PHYSICS_SUBSTEPS = 8
private const val PHYSICS_STIFFNESS = 64.0
