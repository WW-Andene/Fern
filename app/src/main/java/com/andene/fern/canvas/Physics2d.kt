package com.andene.fern.canvas

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * 2D physics for game objects (roadmap item 159): arcade physics, as in Phaser's Arcade or
 * GDevelop's platformer engine - bodies are axis-aligned boxes or circles (an object's turn
 * doesn't tilt its body), pulled by gravity, kept apart by impulses with bounce and friction,
 * and walled in by the scene's edges if the game says so. Each step also reports which bodies
 * touched, for rules and scripts, and which are standing on something.
 */

/** Two bodies touching this step ([b] null: a scene wall), with the normal pointing from [a] to [b]. */
class Contact(val a: GameInstance, val b: GameInstance?, val nx: Double, val ny: Double)

private class Shape(val circle: Boolean, val cx: Double, val cy: Double, val hw: Double, val hh: Double) {
    val radius get() = min(hw, hh)
}

private fun shapeOf(o: GameInstance): Shape {
    val box = o.box
    val c = box.centre
    return Shape(o.def.body.shape == BodyShape.CIRCLE, c.x, c.y, box.width / 2, box.height / 2)
}

/** Overlap of [a] and [b]: the normal from a to b and the depth, or null when apart. */
private fun overlap(a: Shape, b: Shape): Triple<Double, Double, Double>? {
    if (a.circle && b.circle) {
        val dx = b.cx - a.cx
        val dy = b.cy - a.cy
        val d = hypot(dx, dy)
        val depth = a.radius + b.radius - d
        if (depth <= 0) return null
        return if (d < 1e-9) Triple(0.0, 1.0, depth) else Triple(dx / d, dy / d, depth)
    }
    if (!a.circle && !b.circle) {
        val ox = min(a.cx + a.hw, b.cx + b.hw) - max(a.cx - a.hw, b.cx - b.hw)
        val oy = min(a.cy + a.hh, b.cy + b.hh) - max(a.cy - a.hh, b.cy - b.hh)
        if (ox <= 0 || oy <= 0) return null
        return if (ox < oy) Triple(if (b.cx >= a.cx) 1.0 else -1.0, 0.0, ox) else Triple(0.0, if (b.cy >= a.cy) 1.0 else -1.0, oy)
    }
    // A box and a circle: from the box's nearest point to the circle's centre.
    val flip = a.circle
    val box = if (flip) b else a
    val circle = if (flip) a else b
    val nearestX = circle.cx.coerceIn(box.cx - box.hw, box.cx + box.hw)
    val nearestY = circle.cy.coerceIn(box.cy - box.hh, box.cy + box.hh)
    var dx = circle.cx - nearestX
    var dy = circle.cy - nearestY
    var d = hypot(dx, dy)
    val r = circle.radius
    val depth: Double
    if (d < 1e-9) {
        // The centre is inside the box: out along the nearest side.
        val left = circle.cx - (box.cx - box.hw)
        val right = (box.cx + box.hw) - circle.cx
        val top = circle.cy - (box.cy - box.hh)
        val bottom = (box.cy + box.hh) - circle.cy
        val least = minOf(left, right, top, bottom)
        when (least) {
            left -> { dx = -1.0; dy = 0.0 }
            right -> { dx = 1.0; dy = 0.0 }
            top -> { dx = 0.0; dy = -1.0 }
            else -> { dx = 0.0; dy = 1.0 }
        }
        d = 1.0
        depth = least + r
    } else {
        depth = r - d
        if (depth <= 0) return null
    }
    val nx = dx / d
    val ny = dy / d
    return if (flip) Triple(-nx, -ny, depth) else Triple(nx, ny, depth)
}

/**
 * One physics step for [world]'s bodies: gravity into the dynamic bodies' velocity (before the
 * world moves them), then, after they've moved, [ITERATIONS] passes pushing overlapping bodies
 * apart and trading their velocities along the contact. Returns the contacts of the step, and
 * marks each dynamic body [GameInstance.grounded] when something is under it.
 */
internal object Physics2d {
    private const val ITERATIONS = 4
    // Overlap left alone, so resting bodies don't jitter.
    private const val SLOP = 0.05

    fun applyGravity(world: GameWorld, settings: GameSettings, dt: Double) {
        for (o in world.instances) {
            if (o.removed || o.def.body.type != BodyType.DYNAMIC) continue
            o.vx += settings.gravityX * o.def.body.gravityScale * dt
            o.vy += settings.gravityY * o.def.body.gravityScale * dt
        }
    }

    fun resolve(world: GameWorld, settings: GameSettings): List<Contact> {
        val bodies = world.instances.filter { !it.removed && it.def.body.type != BodyType.NONE }
        for (o in bodies) o.grounded = false
        val contacts = LinkedHashMap<Pair<GameInstance, GameInstance?>, Contact>()
        repeat(ITERATIONS) {
            for (i in bodies.indices) for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                if (a.def.body.type != BodyType.DYNAMIC && b.def.body.type != BodyType.DYNAMIC) continue
                val (nx, ny, depth) = overlap(shapeOf(a), shapeOf(b)) ?: continue
                separate(a, b, nx, ny, depth)
                contacts.getOrPut(a to b) { Contact(a, b, nx, ny) }
            }
            if (settings.walls) for (o in bodies) {
                if (o.def.body.type != BodyType.DYNAMIC) continue
                val s = shapeOf(o)
                val hw = if (s.circle) s.radius else s.hw
                val hh = if (s.circle) s.radius else s.hh
                val scene = world.scene
                // Each wall: its normal from the body toward it, and how far the body is past it.
                for ((n, depth) in listOf(
                    Pair(-1.0 to 0.0, scene.min.x - (s.cx - hw)), Pair(1.0 to 0.0, (s.cx + hw) - scene.max.x),
                    Pair(0.0 to -1.0, scene.min.y - (s.cy - hh)), Pair(0.0 to 1.0, (s.cy + hh) - scene.max.y),
                )) {
                    if (depth <= 0) continue
                    separate(o, null, n.first, n.second, depth)
                    contacts.getOrPut(o to null) { Contact(o, null, n.first, n.second) }
                }
            }
        }
        return contacts.values.toList()
    }

    /** Pushes [a] and [b] (null: an immovable wall) apart along the normal (a to b) and settles their velocities. */
    private fun separate(a: GameInstance, b: GameInstance?, nx: Double, ny: Double, depth: Double) {
        val ia = inverseMass(a)
        val ib = if (b == null) 0.0 else inverseMass(b)
        val total = ia + ib
        if (total <= 0) return
        val push = max(depth - SLOP, 0.0) / total
        a.x -= nx * push * ia
        a.y -= ny * push * ia
        if (b != null) {
            b.x += nx * push * ib
            b.y += ny * push * ib
        }
        // Standing: something below a dynamic body (screen y grows downward).
        if (ny > 0.5 && ia > 0) a.grounded = true
        if (ny < -0.5 && b != null && ib > 0) b.grounded = true
        val bvx = b?.vx ?: 0.0
        val bvy = b?.vy ?: 0.0
        val relative = (bvx - a.vx) * nx + (bvy - a.vy) * ny
        if (relative >= 0) return // already parting
        val bounce = max(a.def.body.bounce, b?.def?.body?.bounce ?: a.def.body.bounce)
        val j = -(1 + bounce) * relative / total
        a.vx -= j * ia * nx
        a.vy -= j * ia * ny
        if (b != null) {
            b.vx += j * ib * nx
            b.vy += j * ib * ny
        }
        // Friction along the contact, no more than the normal impulse allows (Coulomb).
        val tx = -ny
        val ty = nx
        val slide = ((b?.vx ?: 0.0) - a.vx) * tx + ((b?.vy ?: 0.0) - a.vy) * ty
        val mu = sqrt(a.def.body.friction * (b?.def?.body?.friction ?: a.def.body.friction))
        val jt = (-slide / total).coerceIn(-j * mu, j * mu)
        a.vx -= jt * ia * tx
        a.vy -= jt * ia * ty
        if (b != null) {
            b.vx += jt * ib * tx
            b.vy += jt * ib * ty
        }
    }

    private fun inverseMass(o: GameInstance) = if (o.def.body.type == BodyType.DYNAMIC) 1 / o.def.body.mass.coerceAtLeast(0.001) else 0.0
}
