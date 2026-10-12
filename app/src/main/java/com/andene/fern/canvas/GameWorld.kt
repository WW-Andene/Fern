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

    /** Where the drawn point [p] is shown now: turned and scaled about the pivot, then moved. */
    fun place(p: WorldPoint): WorldPoint {
        val rad = angle * PI / 180
        val dx = (p.x - pivot.x) * scale
        val dy = (p.y - pivot.y) * scale
        return WorldPoint(pivot.x + dx * cos(rad) - dy * sin(rad) + x + swingX, pivot.y + dx * sin(rad) + dy * cos(rad) + y + swingY)
    }

    /** The object's box as it is now (the drawn box's corners placed, boxed again). */
    val box: WorldBox
        get() {
            val corners = listOf(drawn.min, WorldPoint(drawn.max.x, drawn.min.y), drawn.max, WorldPoint(drawn.min.x, drawn.max.y)).map(::place)
            return WorldBox(WorldPoint(corners.minOf { it.x }, corners.minOf { it.y }), WorldPoint(corners.maxOf { it.x }, corners.maxOf { it.y }))
        }
}

/**
 * The game world (roadmap item 155 onward): the objects as they play inside the [scene], stepped
 * by [step]. Behaviours act in a fixed order each step: velocities from MOVE and BOUNCE are set
 * once at the start, then each step moves, turns, follows paths, swings, bounces off and wraps
 * around the scene's edges, blinks and pulses.
 */
class GameWorld(objects: List<GameObject>, drawnBoxes: Map<String, WorldBox>, val scene: WorldBox, val settings: GameSettings = GameSettings()) {
    val instances: List<GameInstance> = objects.mapNotNull { def -> drawnBoxes[def.id]?.let { GameInstance(def, it) } }

    init {
        for (instance in instances) for (b in instance.def.behaviours) {
            if (b.kind == BehaviourKind.MOVE || b.kind == BehaviourKind.BOUNCE) {
                instance.vx += b.param("vx")
                instance.vy += b.param("vy")
            }
        }
    }

    /** The bodies that touched in the last step (roadmap item 159). */
    var contacts: List<Contact> = emptyList()
        private set

    /** Advances the world by [dt] seconds: gravity, movement and behaviours, then collisions. */
    fun step(dt: Double) {
        Physics2d.applyGravity(this, settings, dt)
        for (instance in instances) {
            if (instance.removed) continue
            instance.time += dt
            instance.x += instance.vx * dt
            instance.y += instance.vy * dt
            for (b in instance.def.behaviours) act(instance, b, dt)
        }
        contacts = Physics2d.resolve(this, settings)
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
