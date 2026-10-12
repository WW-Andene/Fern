package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** A box in world space: [min] and [max] corners. */
data class WorldBox(val min: WorldPoint, val max: WorldPoint) {
    val width get() = max.x - min.x
    val height get() = max.y - min.y
    val centre get() = WorldPoint((min.x + max.x) / 2, (min.y + max.y) / 2)

    fun overlaps(other: WorldBox) = min.x < other.max.x && other.min.x < max.x && min.y < other.max.y && other.min.y < max.y
}

/**
 * One object while the game plays (roadmap item 155): where it has moved from where it was
 * drawn ([x], [y]), its turn ([angle], degrees) and [scale] about its centre ([pivot]), whether
 * it shows, its velocity, its live copy of the properties, and how long it's been playing.
 */
class GameInstance(val def: GameObject, val drawn: WorldBox) {
    val pivot: WorldPoint = drawn.centre

    /** Where it meets things, as drawn: its hitbox's box (roadmap item 157), or the drawing's. */
    val hitDrawn: WorldBox = def.hitbox?.let { h -> WorldBox(WorldPoint(h.minOf { it.x }, h.minOf { it.y }), WorldPoint(h.maxOf { it.x }, h.maxOf { it.y })) } ?: drawn
    var x = 0.0
    var y = 0.0
    var angle = 0.0
    var scale = 1.0
    var visible = true
    var vx = 0.0
    var vy = 0.0
    val properties = HashMap(def.properties)
    var time = 0.0
    /** Removed from play (by a rule or script); stays out until the game restarts. */
    var removed = false
    // Swing from OSCILLATE, added on top of the position without accumulating.
    var swingX = 0.0
    var swingY = 0.0
    // How far along its path a FOLLOW_PATH behaviour has gone.
    var travelled = 0.0
    /** Standing on something this step (physics, roadmap item 159). */
    var grounded = false
    /** Its state machine's current state and how long it's been in it (roadmap item 158). */
    var state: String? = null
    var stateTime = 0.0
    /** The animation frame it shows, while a state plays a tag; null: the frame being edited. */
    var frame: Int? = null
    /** Whether the state's animation has reached its end (only for a state that doesn't loop). */
    var animationDone = false
    // Time spent on the current animation frame.
    var frameClock = 0.0
    /** Why its script stopped (roadmap item 164), shown while playing; null: running, or none. */
    var scriptError: ScriptError? = null

    /** Where the drawn point [p] is shown now: turned and scaled about the pivot, then moved. */
    fun place(p: WorldPoint): WorldPoint {
        val rad = angle * PI / 180
        val dx = (p.x - pivot.x) * scale
        val dy = (p.y - pivot.y) * scale
        return WorldPoint(pivot.x + dx * cos(rad) - dy * sin(rad) + x + swingX, pivot.y + dx * sin(rad) + dy * cos(rad) + y + swingY)
    }

    /** The object's box as it is now (the drawn box's corners placed, boxed again). */
    val box: WorldBox get() = placedBox(drawn)

    /** The hitbox's box as it is now: what physics and touch tests use. */
    val hitBox: WorldBox get() = placedBox(hitDrawn)

    /** The object's centre as it is now. */
    val centre: WorldPoint get() = place(pivot)

    private fun placedBox(b: WorldBox): WorldBox {
        val corners = listOf(b.min, WorldPoint(b.max.x, b.min.y), b.max, WorldPoint(b.min.x, b.max.y)).map(::place)
        return WorldBox(WorldPoint(corners.minOf { it.x }, corners.minOf { it.y }), WorldPoint(corners.maxOf { it.x }, corners.maxOf { it.y }))
    }
}

/**
 * The game world (roadmap item 155 onward): the objects as they play inside the [scene], stepped
 * by [step]. Behaviours act in a fixed order each step: velocities from MOVE and BOUNCE are set
 * once at the start, then each step moves, turns, follows paths, swings, bounces off and wraps
 * around the scene's edges, blinks and pulses.
 */
class GameWorld(
    objects: List<GameObject>,
    drawnBoxes: Map<String, WorldBox>,
    val scene: WorldBox,
    val settings: GameSettings = GameSettings(),
    /** The document's animation, for states that play its tags (roadmap item 158). */
    private val animation: AnimationInfo? = null,
) {
    val instances: List<GameInstance> = objects.mapNotNull { def -> drawnBoxes[def.id]?.let { GameInstance(def, it) } }

    // Each object's script (roadmap item 164), parsed once; one that doesn't parse shows its error instead.
    private val scripts: Map<GameInstance, GameScriptRunner> = instances.filter { it.def.script.isNotBlank() }.mapNotNull { o ->
        try {
            o to GameScriptRunner(GameScript.parse(o.def.script), o)
        } catch (e: ScriptError) {
            o.scriptError = e
            null
        }
    }.toMap()

    init {
        for (instance in instances) for (b in instance.def.behaviours) {
            if (b.kind == BehaviourKind.MOVE || b.kind == BehaviourKind.BOUNCE) {
                instance.vx += b.param("vx")
                instance.vy += b.param("vy")
            }
        }
    }

    /** What the player is doing (roadmap item 160), fed by the play screen. */
    val input = GameInputState(settings.actions)

    /** The bodies that touched in the last step (roadmap item 159). */
    var contacts: List<Contact> = emptyList()
        private set

    /** Advances the world by [dt] seconds: gravity, movement and behaviours, then collisions. */
    fun step(dt: Double) {
        input.beginStep()
        Physics2d.applyGravity(this, settings, dt)
        for (instance in instances) {
            if (instance.removed) continue
            instance.time += dt
            instance.x += instance.vx * dt
            instance.y += instance.vy * dt
            for (b in instance.def.behaviours) act(instance, b, dt)
        }
        contacts = Physics2d.resolve(this, settings)
        holdLinks()
        // Rules (roadmap item 156), then scripts (item 164), see where things ended up and what touched.
        for (instance in instances) if (!instance.removed) GameActions.runRules(this, instance)
        for ((instance, script) in scripts) {
            if (instance.removed || instance.scriptError != null) continue
            try {
                script.step(this, dt)
            } catch (e: ScriptError) {
                instance.scriptError = e
            }
        }
        for (instance in instances) if (!instance.removed) runStateMachine(instance, dt)
    }

    /** The objects [o] touches this step called [name] (empty: any object): bodies in contact, or hitboxes overlapping. */
    fun touching(o: GameInstance, name: String): List<GameInstance> {
        val inContact = contacts.mapNotNull { c ->
            when {
                c.a === o -> c.b
                c.b === o -> c.a
                else -> null
            }
        }
        return (inContact + overlapping(o)).distinct().filter { !it.removed && (name.isEmpty() || it.def.name == name) }
    }

    /** Moves [o]'s state machine (roadmap item 158) to the state called [name], if it has one and isn't in it. */
    fun goToState(o: GameInstance, name: String) {
        if (o.state == name) return
        o.def.stateMachine?.states?.firstOrNull { it.name == name }?.let { enterState(o, it) }
    }

    /** The objects whose hitbox overlaps [o]'s, bodies or not (for touch conditions on objects without physics). */
    fun overlapping(o: GameInstance): List<GameInstance> {
        val box = o.hitBox
        return instances.filter { it !== o && !it.removed && it.hitBox.overlaps(box) }
    }

    /**
     * [o]'s state machine for one step (roadmap item 158): into its first state when it has
     * none yet, then the first transition from this state (or from any state) whose conditions
     * all hold, then the state's animation moves on at the document's frame rate, following the
     * X-sheet holds, looping or stopping at its end.
     */
    private fun runStateMachine(o: GameInstance, dt: Double) {
        val machine = o.def.stateMachine ?: return
        if (machine.states.isEmpty()) return
        if (o.state == null) enterState(o, machine.states.firstOrNull { it.name == machine.initial } ?: machine.states.first())
        o.stateTime += dt
        machine.transitions.firstOrNull { t ->
            (t.from == null || t.from == o.state) && t.to != o.state && t.conditions.all { conditionHolds(it, o, this) }
        }?.let { t -> machine.states.firstOrNull { it.name == t.to }?.let { enterState(o, it) } }
        val state = machine.states.firstOrNull { it.name == o.state } ?: return
        val anim = animation ?: return
        val tag = anim.tags.firstOrNull { it.name == state.tag } ?: return
        val frame = o.frame ?: return
        o.frameClock += dt
        val hold = (anim.holds[frame] ?: 1).toDouble() / anim.fps.coerceAtLeast(1)
        if (o.frameClock < hold) return
        o.frameClock -= hold
        val last = tag.to.coerceAtMost(anim.frameCount - 1)
        val first = tag.from.coerceAtMost(last)
        val reverse = tag.direction == TagDirection.REVERSE
        val next = if (reverse) frame - 1 else frame + 1
        if (next in first..last) {
            o.frame = next
        } else if (state.loop) {
            o.frame = if (reverse) last else first
        } else {
            o.animationDone = true
        }
    }

    private fun enterState(o: GameInstance, state: AnimState) {
        o.state = state.name
        o.stateTime = 0.0
        o.animationDone = false
        o.frameClock = 0.0
        val tag = animation?.tags?.firstOrNull { it.name == state.tag }
        o.frame = tag?.let { if (it.direction == TagDirection.REVERSE) it.to else it.from }
    }

    /**
     * Drawn links (roadmap item 157), after the bodies have settled: an attachment carries its
     * object along with the other; a rod keeps their centres at its length, a rope within it,
     * moving dynamic bodies only (a link between two fixed objects holds nothing).
     */
    private fun holdLinks() {
        for (link in settings.links) {
            val a = instanceOf(link.from) ?: continue
            val b = instanceOf(link.to) ?: continue
            if (a.removed || b.removed) continue
            if (link.kind == LinkKind.ATTACH) {
                b.x = a.x
                b.y = a.y
                b.vx = a.vx
                b.vy = a.vy
                continue
            }
            val ca = a.centre
            val cb = b.centre
            val dx = cb.x - ca.x
            val dy = cb.y - ca.y
            val d = hypot(dx, dy)
            if (d < 1e-9 || (link.kind == LinkKind.ROPE && d <= link.length)) continue
            val ia = if (a.def.body.type == BodyType.DYNAMIC) 1 / a.def.body.mass.coerceAtLeast(0.001) else 0.0
            val ib = if (b.def.body.type == BodyType.DYNAMIC) 1 / b.def.body.mass.coerceAtLeast(0.001) else 0.0
            if (ia + ib <= 0) continue
            val nx = dx / d
            val ny = dy / d
            val error = (d - link.length) / (ia + ib)
            a.x += nx * error * ia
            a.y += ny * error * ia
            b.x -= nx * error * ib
            b.y -= ny * error * ib
            // No speed left along the link (a rope only stops it stretching further).
            val relative = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
            if (link.kind == LinkKind.ROD || relative > 0) {
                val j = relative / (ia + ib)
                a.vx += nx * j * ia
                a.vy += ny * j * ia
                b.vx -= nx * j * ib
                b.vy -= ny * j * ib
            }
        }
    }

    private fun act(o: GameInstance, b: Behaviour, dt: Double) {
        when (b.kind) {
            BehaviourKind.MOVE -> {} // its velocity was set at the start
            BehaviourKind.ROTATE -> o.angle = (o.angle + b.param("speed") * dt) % 360
            BehaviourKind.OSCILLATE -> {
                val period = b.param("period").coerceAtLeast(0.01)
                val s = sin(2 * PI * o.time / period)
                o.swingX = b.param("dx") * s
                o.swingY = b.param("dy") * s
            }
            BehaviourKind.FOLLOW_PATH -> {
                val path = b.path ?: return
                if (path.size < 2) return
                val length = (1 until path.size).sumOf { hypot(path[it].x - path[it - 1].x, path[it].y - path[it - 1].y) }
                if (length <= 0) return
                o.travelled += b.param("speed") * dt
                val along = if (b.param("loop") >= 0.5) o.travelled - floor(o.travelled / length) * length else o.travelled.coerceAtMost(length)
                val at = pointAlong(path, along)
                // The object's centre rides the path.
                o.x = at.x - o.pivot.x
                o.y = at.y - o.pivot.y
            }
            BehaviourKind.BOUNCE -> {
                val box = o.box
                if (box.min.x < scene.min.x && o.vx < 0 || box.max.x > scene.max.x && o.vx > 0) o.vx = -o.vx
                if (box.min.y < scene.min.y && o.vy < 0 || box.max.y > scene.max.y && o.vy > 0) o.vy = -o.vy
            }
            BehaviourKind.WRAP -> {
                val box = o.box
                if (box.min.x > scene.max.x) o.x -= scene.width + box.width
                if (box.max.x < scene.min.x) o.x += scene.width + box.width
                if (box.min.y > scene.max.y) o.y -= scene.height + box.height
                if (box.max.y < scene.min.y) o.y += scene.height + box.height
            }
            BehaviourKind.BLINK -> {
                val period = b.param("period").coerceAtLeast(0.01)
                o.visible = (o.time % period) < period / 2
            }
            BehaviourKind.PULSE -> {
                val period = b.param("period").coerceAtLeast(0.01)
                o.scale = 1 + b.param("amount") * sin(2 * PI * o.time / period)
            }
            BehaviourKind.PLATFORMER -> {
                val direction = (if (input.isDown("right")) 1 else 0) - (if (input.isDown("left")) 1 else 0)
                o.vx = direction * b.param("speed")
                if (input.justPressed("jump") && o.grounded) o.vy = -b.param("jump")
            }
            BehaviourKind.TOP_DOWN -> {
                val dx = ((if (input.isDown("right")) 1 else 0) - (if (input.isDown("left")) 1 else 0)).toDouble()
                val dy = ((if (input.isDown("down")) 1 else 0) - (if (input.isDown("up")) 1 else 0)).toDouble()
                val length = hypot(dx, dy)
                o.vx = if (length > 0) dx / length * b.param("speed") else 0.0
                o.vy = if (length > 0) dy / length * b.param("speed") else 0.0
            }
        }
    }

    /** The playing object for group [id], if it's an object. */
    fun instanceOf(id: String?): GameInstance? = if (id == null) null else instances.firstOrNull { it.def.id == id }
}

/** The point [distance] along the polyline [path] from its start. */
fun pointAlong(path: List<WorldPoint>, distance: Double): WorldPoint {
    var left = distance
    for (i in 1 until path.size) {
        val a = path[i - 1]
        val b = path[i]
        val segment = hypot(b.x - a.x, b.y - a.y)
        if (left <= segment && segment > 0) {
            val t = left / segment
            return WorldPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        left -= segment
    }
    return path.last()
}
