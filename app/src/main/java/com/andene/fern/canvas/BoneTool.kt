package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The BONE tool (roadmap item 83), with its own state rather than in [CanvasState] (roadmap item
 * 167): adding bones, posing them (turning, moving a whole skeleton, inverse kinematics) and
 * painting their weights, with the gesture in progress. The skeleton and the ink it carries are
 * the document's, reached through [host].
 */
class BoneTool(private val host: Host) {
    interface Host {
        /** Screen pixels per world unit. */
        val scale: Double

        /** The bones of the frame on screen. */
        val currentBones: List<Bone>

        /** The bone tapped last, for deleting and weight painting; null when none. */
        var selectedBoneId: String?

        /** Whether turning a bone looks up a two-bone chain (inverse kinematics, roadmap item 85). */
        val inverseKinematics: Boolean

        fun recordHistory()

        /** Sets weight [value] on the points of the selected bone's ink within the brush of [at] (roadmap item 84). */
        fun paintWeights(at: WorldPoint, value: Double)

        /** Starts turning [bone] by hand from [pointer], capturing what moves with it. */
        fun startTurn(bone: Bone, pointer: WorldPoint): BoneTurn

        /** Puts [turn]'s bones and ink at [angle] radians from where it started. */
        fun applyTurn(turn: BoneTurn, angle: Double)

        /** Turns bone [endId]'s chain so its tail reaches toward [target] (inverse kinematics). */
        fun reachToward(endId: String, target: WorldPoint)

        /** Moves the whole skeleton rooted at [rootId], and its ink, by [delta]. */
        fun moveSkeleton(rootId: String, delta: WorldPoint)

        /** Adds a bone from [head] to [tail] chained to [parentId], selected, as one undo step. */
        fun addBone(parentId: String?, head: WorldPoint, tail: WorldPoint)

        /** Bones (and their ink) were posed: save and redraw. */
        fun posed()

        /** Weights were painted: save and redraw the ink. */
        fun weightsPainted()
    }

    /** True to pose (rotate, move) the skeleton, false to add bones. */
    var posing by mutableStateOf(false)

    /**
     * Weight painting (roadmap item 84): dragging over ink bound to the selected bone sets how
     * much each point under the brush follows that bone rather than its parent
     * ([Stroke.jointWeights]) to [weightValue], overriding the automatic weights the next time a
     * joint is turned. Each drag is one undo step.
     */
    var paintingWeights by mutableStateOf(false)

    /** The weight painting applies, 0 (follow the parent) to 1 (follow the bone itself). */
    var weightValue by mutableDoubleStateOf(1.0)

    /** The bone being drawn by an add drag, for the overlay; null otherwise. */
    var draft by mutableStateOf<Pair<WorldPoint, WorldPoint>?>(null)
        private set

    private enum class Kind { ADD, ROTATE, MOVE, REACH, PAINT, NONE }

    private data class Gesture(val kind: Kind, val boneId: String?, val last: WorldPoint, val parentId: String? = null, val head: WorldPoint = last)

    private var gesture: Gesture? = null
    private var turn: BoneTurn? = null

    /**
     * A press. Adding: starts a new bone here, chained to a bone whose tail is within reach (its
     * joint snaps onto that tail). Posing: a press near a bone's tail turns that bone about its
     * joint; a press elsewhere on a root bone moves its whole skeleton. A press on a bone also
     * selects it.
     */
    fun begin(world: WorldPoint) {
        val reach = HIT_SCREEN / host.scale.coerceAtLeast(1e-300)
        val frameBones = host.currentBones
        if (paintingWeights) {
            if (host.selectedBoneId == null) host.selectedBoneId = boneAt(frameBones, world, reach)?.id
            val selected = host.selectedBoneId ?: return
            host.recordHistory()
            gesture = Gesture(Kind.PAINT, selected, world)
            host.paintWeights(world, weightValue)
            return
        }
        val hit = boneAt(frameBones, world, reach)
        host.selectedBoneId = hit?.id
        fun tailNear() = frameBones.filter { hypot(it.tail.x - world.x, it.tail.y - world.y) <= reach }.minByOrNull { hypot(it.tail.x - world.x, it.tail.y - world.y) }
        if (!posing) {
            val parent = tailNear()
            val head = parent?.tail ?: world
            gesture = Gesture(Kind.ADD, null, world, parent?.id, head)
            draft = head to world
            return
        }
        val tipBone = tailNear()
        val started = when {
            tipBone != null && host.inverseKinematics && tipBone.parentId != null -> Gesture(Kind.REACH, tipBone.id, world)
            tipBone != null -> Gesture(Kind.ROTATE, tipBone.id, world)
            hit != null && hit.parentId == null -> Gesture(Kind.MOVE, hit.id, world)
            hit != null -> Gesture(Kind.ROTATE, hit.id, world)
            else -> Gesture(Kind.NONE, null, world)
        }
        if (started.kind != Kind.NONE) host.recordHistory()
        gesture = started
        turn = if (started.kind == Kind.ROTATE) host.startTurn(frameBones.first { it.id == started.boneId }, world) else null
    }

    fun move(world: WorldPoint) {
        val current = gesture ?: return
        when (current.kind) {
            Kind.ADD -> {
                draft = current.head to world
                gesture = current.copy(last = world)
            }
            Kind.ROTATE -> {
                val turning = turn ?: return
                val start = turning.startBones.associateBy { it.id }
                // Joint limits hold for a hand-turned bone as for inverse kinematics.
                val turned = current.boneId?.let { start[it] } ?: return
                val angle = limitedTurn(
                    turned,
                    turned.parentId?.let { start[it] },
                    normalizeAngle(atan2(world.y - turning.center.y, world.x - turning.center.x) - turning.startAngle),
                )
                host.applyTurn(turning, angle)
                gesture = current.copy(last = world)
            }
            Kind.PAINT -> host.paintWeights(world, weightValue)
            Kind.REACH -> {
                host.reachToward(current.boneId ?: return, world)
                gesture = current.copy(last = world)
            }
            Kind.MOVE -> {
                host.moveSkeleton(current.boneId ?: return, world - current.last)
                gesture = current.copy(last = world)
            }
            Kind.NONE -> {}
        }
    }

    fun end() {
        val finished = gesture ?: return
        gesture = null
        turn = null
        draft = null
        when (finished.kind) {
            Kind.ADD -> {
                val tail = finished.last
                // Too short to be a deliberate bone (a tap): nothing is added.
                if (hypot(tail.x - finished.head.x, tail.y - finished.head.y) < MIN_SCREEN / host.scale.coerceAtLeast(1e-300)) return
                host.addBone(finished.parentId, finished.head, tail)
            }
            Kind.ROTATE, Kind.MOVE, Kind.REACH -> host.posed()
            Kind.PAINT -> host.weightsPainted()
            Kind.NONE -> {}
        }
    }

    /**
     * Follows a floating-origin rebase: the world moved by -[offset]. A turn holds starting
     * geometry in the old coordinates; one-finger drags don't pan, so this can't normally happen
     * mid-turn, but if it does the turn simply stops.
     */
    fun shiftBy(offset: WorldPoint) {
        gesture = gesture?.let { it.copy(last = it.last - offset, head = it.head - offset) }
        draft = draft?.let { (head, tail) -> (head - offset) to (tail - offset) }
        turn = null
    }

    private companion object {
        // Screen pixels: how near a bone or tail a press catches it, and the shortest bone an add drag makes.
        const val HIT_SCREEN = 24.0
        const val MIN_SCREEN = 16.0
    }
}
