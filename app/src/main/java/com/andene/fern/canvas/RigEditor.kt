package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Editing a rig the way Spine does (roadmap items 250-253, 272, 353), for [Tool.RIG]:
 *
 * - **Setup mode** builds the skeleton. Drawing bones, a drag draws a bone (from near a bone's
 *   tip, it becomes that bone's child); adjusting, dragging a tip turns and stretches the bone
 *   and dragging its body moves it. The drawing doesn't move: it is attached in this pose.
 * - **Animate mode** poses it: dragging a tip turns the bone, dragging a root bone's body moves
 *   it, and each change keys the active animation at the current frame (auto-key), so the
 *   timeline and playback show the motion. The drawing follows its bones by weight.
 *
 * The artist's own ink is attached with [attach]: it leaves the canvas and lives in the rig as
 * an attachment in a slot, drawn posed from then on ([posedStrokes]).
 *
 * [host] gives access to the canvas: the frame shown, the view scale, and undo.
 */
class RigEditor(private val host: Host) {
    interface Host {
        val currentFrame: Int
        val viewScale: Double
        fun recordHistory()
        fun rigChanged(rig: Rig)
    }

    var rig by mutableStateOf(Rig(emptyList()))
        private set

    /** Setup mode (building the skeleton) or animate mode (posing and keying). */
    var setupMode by mutableStateOf(true)

    /** In setup mode: drags draw new bones (true) or adjust existing ones (false). */
    var drawingBones by mutableStateOf(true)

    var animationId by mutableStateOf<String?>(null)

    /**
     * Inverse kinematics while animating (roadmap item 288): dragging the tip of a bone that has a
     * parent turns both so the tip reaches the finger - an exact two-bone solve, Spine's choice,
     * since longer chains have no single answer. [bendPositive] chooses which way the joint bends.
     */
    var inverseKinematics by mutableStateOf(false)
    var bendPositive by mutableStateOf(true)

    var selectedBoneId by mutableStateOf<String?>(null)

    /** Bumped whenever the posed drawing may have changed, so cached renders redraw. */
    var revision by mutableIntStateOf(0)
        private set

    /** The bone being drawn by a drag in setup mode, for the overlay. */
    var draft by mutableStateOf<Pair<WorldPoint, WorldPoint>?>(null)
        private set

    val activeAnimation: RigAnimation? get() = rig.animations.firstOrNull { it.id == animationId }

    /** Each bone's world transform as shown: the setup pose, or the animation at the current frame. */
    fun pose(): Map<String, Affine> =
        if (setupMode) rig.setupPose() else rig.pose(activeAnimation, host.currentFrame.toDouble())

    fun load(loaded: Rig) {
        rig = loaded
        animationId = loaded.animations.firstOrNull()?.id
        selectedBoneId = null
        revision++
    }

    /** Puts back a rig from undo or redo. */
    fun restore(saved: Rig) {
        if (saved == rig) return
        rig = saved
        if (rig.animations.none { it.id == animationId }) animationId = rig.animations.firstOrNull()?.id
        if (rig.bones.none { it.id == selectedBoneId }) selectedBoneId = null
        revision++
        host.rigChanged(rig)
    }

    private fun change(new: Rig) {
        rig = new
        revision++
        host.rigChanged(new)
    }

    /**
     * The attached drawing as it is shown now: every slot's attachment, in slot order, carried
     * to the current pose. Copies, never the stored setup ink.
     */
    fun posedStrokes(): List<Stroke> {
        if (rig.slots.isEmpty()) return emptyList()
        // The scene is drawn once per tile and per frame of a drag: pose once per state.
        val key = listOf(rig, setupMode, animationId, host.currentFrame)
        posedCache?.let { (k, strokes) -> if (k == key) return strokes }
        return computePosedStrokes().also { posedCache = key to it }
    }

    private var posedCache: Pair<List<Any?>, List<Stroke>>? = null

    private fun computePosedStrokes(): List<Stroke> {
        val pose = pose()
        val setup = rig.setupPose()
        val byId = rig.attachments.associateBy { it.id }
        val out = ArrayList<Stroke>()
        for (slot in rig.slots) {
            val attachment = byId[slot.attachmentId] ?: continue
            val posed = rig.deform(attachment, pose, setup)
            attachment.strokes.forEachIndexed { i, stroke ->
                out += stroke.deepCopy().onAnimFrame(host.currentFrame).also { it.setPoints(posed[i]) }
            }
        }
        return out
    }

    // ---- Gestures ----

    private enum class Drag { CREATE, TIP, BODY, NONE }

    private var drag = Drag.NONE
    private var dragBoneId: String? = null
    private var dragStart = WorldPoint.Zero
    private var dragLast = WorldPoint.Zero
    private var draftParentId: String? = null

    private val reach: Double get() = BONE_REACH_SCREEN / host.viewScale.coerceAtLeast(1e-300)

    fun begin(world: WorldPoint) {
        val pose = pose()
        val tipHit = rig.bones.filter { pose[it.id] != null }.minByOrNull { hypot(boneTip(it, pose.getValue(it.id)).x - world.x, boneTip(it, pose.getValue(it.id)).y - world.y) }
            ?.takeIf { val t = boneTip(it, pose.getValue(it.id)); hypot(t.x - world.x, t.y - world.y) <= reach }
        val bodyHit = rig.bones.firstOrNull { b ->
            val w = pose[b.id] ?: return@firstOrNull false
            distanceToPolyline(listOf(boneOrigin(w), boneTip(b, w)), world) <= reach
        }
        dragStart = world
        dragLast = world
        when {
            setupMode && drawingBones -> {
                drag = Drag.CREATE
                draftParentId = tipHit?.id
                selectedBoneId = tipHit?.id
                draft = (tipHit?.let { boneTip(it, pose.getValue(it.id)) } ?: world) to world
            }
            tipHit != null -> {
                drag = Drag.TIP
                dragBoneId = tipHit.id
                selectedBoneId = tipHit.id
                host.recordHistory()
            }
            bodyHit != null -> {
                drag = Drag.BODY
                dragBoneId = bodyHit.id
                selectedBoneId = bodyHit.id
                host.recordHistory()
            }
            else -> {
                drag = Drag.NONE
                selectedBoneId = null
            }
        }
    }

    fun move(world: WorldPoint) {
        when (drag) {
            Drag.CREATE -> draft = draft?.first?.let { it to world }
            Drag.TIP -> dragBoneId?.let { pointTipAt(it, world) }
            Drag.BODY -> dragBoneId?.let { moveBody(it, world - dragLast) }
            Drag.NONE -> {}
        }
        dragLast = world
    }

    fun end() {
        if (drag == Drag.CREATE) {
            val (head, tail) = draft ?: (dragStart to dragLast)
            if (hypot(tail.x - head.x, tail.y - head.y) * host.viewScale >= MIN_BONE_SCREEN) addBone(draftParentId, head, tail)
        }
        draft = null
        drag = Drag.NONE
        dragBoneId = null
    }

    /** Adds a bone from [head] to [tail] in world space, a child of [parentId] when given. */
    private fun addBone(parentId: String?, head: WorldPoint, tail: WorldPoint) {
        host.recordHistory()
        val setup = rig.setupPose()
        val parentWorld = parentId?.let { setup[it] } ?: Affine.IDENTITY
        val localHead = parentWorld.inverse().apply(head)
        val worldAngle = atan2(tail.y - head.y, tail.x - head.x)
        val bone = RigBone(
            UUID.randomUUID().toString(),
            "bone ${rig.bones.size + 1}",
            parentId,
            localHead.x,
            localHead.y,
            normalizeAngle(worldAngle - (if (parentId != null) parentWorld.angle else 0.0)),
            hypot(tail.x - head.x, tail.y - head.y) / scaleAlongX(parentWorld, parentId),
        )
        change(rig.copy(bones = rig.bones + bone))
        selectedBoneId = bone.id
    }

    /** A parent's scale along its own x axis, so a child's length stays in world units at creation. */
    private fun scaleAlongX(parent: Affine, parentId: String?): Double = if (parentId == null) 1.0 else hypot(parent.a, parent.b).coerceAtLeast(1e-9)

    /** Turns bone [id] so its tip points at [world]; in setup mode its length follows the finger too. */
    private fun pointTipAt(id: String, world: WorldPoint) {
        val bone = rig.bones.firstOrNull { it.id == id } ?: return
        val pose = pose()
        val w = pose[id] ?: return
        val origin = boneOrigin(w)
        val worldAngle = atan2(world.y - origin.y, world.x - origin.x)
        val parentAngle = bone.parentId?.let { pose[it]?.angle } ?: 0.0
        val localRotation = normalizeAngle(worldAngle - parentAngle)
        val parent = bone.parentId?.let { id -> rig.bones.firstOrNull { it.id == id } }
        if (!setupMode && inverseKinematics && parent != null) {
            reachTwoBones(parent, bone, world, pose)
            return
        }
        if (setupMode) {
            val length = hypot(world.x - origin.x, world.y - origin.y) / hypot(w.a, w.b).coerceAtLeast(1e-9) * bone.scaleX
            replaceBone(bone.copy(rotation = localRotation, length = length.coerceAtLeast(1e-9)))
        } else {
            setKey(id, BoneProperty.ROTATE, normalizeAngle(localRotation - bone.rotation))
        }
    }

    /**
     * Two-bone IK: turns [parent] and [child] so the child's tip lands on [target] (or as near as
     * their lengths allow), the joint bending the [bendPositive] way, and keys both rotations.
     */
    private fun reachTwoBones(parent: RigBone, child: RigBone, target: WorldPoint, pose: Map<String, Affine>) {
        val pw = pose[parent.id] ?: return
        val cw = pose[child.id] ?: return
        val origin = boneOrigin(pw)
        val l1 = hypot(boneOrigin(cw).x - origin.x, boneOrigin(cw).y - origin.y)
        val l2 = hypot(boneTip(child, cw).x - boneOrigin(cw).x, boneTip(child, cw).y - boneOrigin(cw).y)
        if (l1 < 1e-9 || l2 < 1e-9) return
        val d = hypot(target.x - origin.x, target.y - origin.y).coerceIn(kotlin.math.abs(l1 - l2) + 1e-9, l1 + l2 - 1e-9)
        val toTarget = atan2(target.y - origin.y, target.x - origin.x)
        val inner = kotlin.math.acos(((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)).coerceIn(-1.0, 1.0))
        val parentWorldAngle = toTarget + if (bendPositive) -inner else inner
        val joint = WorldPoint(origin.x + l1 * kotlin.math.cos(parentWorldAngle), origin.y + l1 * kotlin.math.sin(parentWorldAngle))
        val childWorldAngle = atan2(target.y - joint.y, target.x - joint.x)
        // The child's setup offset from its parent's direction, so a child not at the parent's tip stays where it was.
        val childSetupOffset = cw.angle - pw.angle - (child.rotation + (currentKey(child.id, BoneProperty.ROTATE) ?: 0.0))
        val grandAngle = parent.parentId?.let { pose[it]?.angle } ?: 0.0
        setKey(parent.id, BoneProperty.ROTATE, normalizeAngle(parentWorldAngle - grandAngle - parent.rotation))
        setKey(child.id, BoneProperty.ROTATE, normalizeAngle(childWorldAngle - parentWorldAngle - childSetupOffset - child.rotation))
    }

    private fun currentKey(boneId: String, property: BoneProperty): Double? =
        activeAnimation?.timelines?.firstOrNull { it.boneId == boneId && it.property == property }?.valueAt(host.currentFrame.toDouble())

    /** Moves bone [id] by [delta] (world units): its setup position, or a translation key when animating. */
    private fun moveBody(id: String, delta: WorldPoint) {
        val bone = rig.bones.firstOrNull { it.id == id } ?: return
        val parentWorld = bone.parentId?.let { pose()[it] } ?: Affine.IDENTITY
        // The delta in the parent's frame, ignoring its translation.
        val inv = parentWorld.inverse()
        val local = WorldPoint(inv.a * delta.x + inv.c * delta.y, inv.b * delta.x + inv.d * delta.y)
        if (setupMode) {
            replaceBone(bone.copy(x = bone.x + local.x, y = bone.y + local.y))
        } else {
            val anim = activeAnimation
            val time = host.currentFrame.toDouble()
            val x = anim?.timelines?.firstOrNull { it.boneId == id && it.property == BoneProperty.X }?.valueAt(time) ?: 0.0
            val y = anim?.timelines?.firstOrNull { it.boneId == id && it.property == BoneProperty.Y }?.valueAt(time) ?: 0.0
            setKey(id, BoneProperty.X, x + local.x)
            setKey(id, BoneProperty.Y, y + local.y)
        }
    }

    private fun replaceBone(bone: RigBone) {
        change(rig.copy(bones = rig.bones.map { if (it.id == bone.id) bone else it }))
    }

    /**
     * Keys [property] of bone [boneId] to [value] at the current frame in the active animation
     * (made, named "animation 1", if there is none): replaces a key at that frame or adds one.
     */
    private fun setKey(boneId: String, property: BoneProperty, value: Double) {
        val time = host.currentFrame.toDouble()
        var anim = activeAnimation
        val animations = rig.animations.toMutableList()
        if (anim == null) {
            anim = RigAnimation(UUID.randomUUID().toString(), "animation ${animations.size + 1}", 1, emptyList())
            animations += anim
            animationId = anim.id
        }
        val timelines = anim.timelines.toMutableList()
        val index = timelines.indexOfFirst { it.boneId == boneId && it.property == property }
        val keys = (if (index == -1) emptyList() else timelines[index].keys).filter { kotlin.math.abs(it.time - time) > 1e-9 } + Key(time, value)
        val timeline = BoneTimeline(boneId, property, keys.sortedBy { it.time })
        if (index == -1) timelines += timeline else timelines[index] = timeline
        val updated = anim.copy(timelines = timelines, length = maxOf(anim.length, host.currentFrame + 1))
        change(rig.copy(animations = animations.map { if (it.id == updated.id) updated else it }))
    }

    /** Adds an empty animation named [name] and makes it active (roadmap item 253). */
    fun addAnimation(name: String) {
        host.recordHistory()
        val anim = RigAnimation(UUID.randomUUID().toString(), name, 1, emptyList())
        change(rig.copy(animations = rig.animations + anim))
        animationId = anim.id
        setupMode = false
    }

    /**
     * Attaches [ink] to the rig (roadmap item 353): it becomes one attachment in a new slot on
     * the selected bone (or the bone nearest its middle), each point following the bones nearest
     * it by weight, so one outline drawn across a joint bends there. The ink is kept exactly as
     * drawn, in the setup pose. Returns false without bones or ink.
     */
    fun attach(ink: List<Stroke>, name: String): Boolean {
        if (rig.bones.isEmpty() || ink.isEmpty()) return false
        val setup = rig.setupPose()
        val center = WorldPoint((ink.minOf { it.minX } + ink.maxOf { it.maxX }) / 2, (ink.minOf { it.minY } + ink.maxOf { it.maxY }) / 2)
        val bone = selectedBoneId?.let { id -> rig.bones.firstOrNull { it.id == id } }
            ?: rig.bones.minBy { b -> setup[b.id]?.let { distanceToPolyline(listOf(boneOrigin(it), boneTip(b, it)), center) } ?: Double.MAX_VALUE }
        val strokes = ink.map { it.deepCopy() }
        val weights = strokes.map { autoWeights(it.points, rig.bones, setup) }
        val attachment = RigAttachment(UUID.randomUUID().toString(), name, strokes, weights)
        val slot = RigSlot(UUID.randomUUID().toString(), name, bone.id, attachment.id)
        change(rig.copy(slots = rig.slots + slot, attachments = rig.attachments + attachment))
        return true
    }

    /**
     * Deletes the selected bone and its children. Ink attached to slots on them comes back to the
     * canvas as it was drawn, returned by this call for the caller to put back.
     */
    fun deleteSelectedBone(): List<Stroke> {
        val id = selectedBoneId ?: return emptyList()
        val removed = HashSet<String>().apply { add(id) }
        var grew = true
        while (grew) {
            grew = false
            for (b in rig.bones) if (b.parentId in removed && removed.add(b.id)) grew = true
        }
        val freedSlots = rig.slots.filter { it.boneId in removed }
        val freedAttachments = freedSlots.mapNotNull { it.attachmentId }.toSet()
        val returned = rig.attachments.filter { it.id in freedAttachments }.flatMap { it.strokes }
        // Ink attached elsewhere that also followed a removed bone keeps only its other bones.
        val attachments = rig.attachments.filter { it.id !in freedAttachments }.map { a ->
            a.copy(weights = a.weights.map { stroke -> stroke.map { point -> renormalised(point.filter { it.first !in removed }) } })
        }
        change(Rig(rig.bones.filter { it.id !in removed }, rig.slots.filter { it.boneId !in removed }, attachments, rig.animations.map { anim -> anim.copy(timelines = anim.timelines.filter { it.boneId !in removed }) }))
        selectedBoneId = null
        return returned
    }

    private fun renormalised(point: List<Pair<String, Double>>): List<Pair<String, Double>> {
        val total = point.sumOf { it.second }
        return if (total <= 0.0) emptyList() else point.map { it.first to it.second / total }
    }

    fun renameBone(id: String, name: String) {
        if (name.isBlank()) return
        change(rig.copy(bones = rig.bones.map { if (it.id == id) it.copy(name = name) else it }))
    }

    /**
     * Moves slot [id] [by] places in the draw order (roadmap items 274, 280): later slots draw on
     * top, so an arm can go behind the body.
     */
    fun moveSlot(id: String, by: Int) {
        val from = rig.slots.indexOfFirst { it.id == id }
        val to = from + by
        if (from == -1 || to !in rig.slots.indices) return
        host.recordHistory()
        val slots = rig.slots.toMutableList()
        slots.add(to, slots.removeAt(from))
        change(rig.copy(slots = slots))
    }

    /** Removes slot [id] and its attachment; returns the attachment's ink, as drawn, for the canvas. */
    fun detachSlot(id: String): List<Stroke> {
        val slot = rig.slots.firstOrNull { it.id == id } ?: return emptyList()
        val attachment = rig.attachments.firstOrNull { it.id == slot.attachmentId }
        change(rig.copy(slots = rig.slots - slot, attachments = rig.attachments.filter { it.id != slot.attachmentId }))
        return attachment?.strokes.orEmpty()
    }

    /** Called when the canvas re-anchors its origin: every setup position moves with the drawing. */
    fun shiftBy(offset: WorldPoint) {
        if (rig.bones.isEmpty() && rig.attachments.isEmpty()) return
        val roots = rig.bones.map { if (it.parentId == null) it.copy(x = it.x - offset.x, y = it.y - offset.y) else it }
        val attachments = rig.attachments.map { a -> a.copy(strokes = a.strokes.map { s -> s.deepCopy().also { it.shiftBy(offset) } }) }
        change(rig.copy(bones = roots, attachments = attachments))
    }

    companion object {
        // A finger this close (screen px) to a bone's tip or body grabs it.
        private const val BONE_REACH_SCREEN = 24.0

        // A drag shorter than this (screen px) draws no bone: it was a tap.
        private const val MIN_BONE_SCREEN = 16.0
    }
}
