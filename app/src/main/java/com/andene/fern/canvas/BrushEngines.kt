package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Fine particles a spray dab scatters, per unit of the tip's radius squared (more for a bigger tip). */
private const val SPRAY_DENSITY = 0.35
private const val SPRAY_MAX_PARTICLES = 256
private const val SPRAY_PARTICLE_RADIUS = 0.06 // of the tip's radius

/** Hairs across a bristle brush, and how much of its paint each loses per tip-width travelled. */
private const val BRISTLE_HAIRS = 12
private const val BRISTLE_DRY_PER_WIDTH = 0.004

/**
 * One dab of the spray engine (roadmap item 140, Krita's spray brush): particles scattered over
 * the tip's disc at [center] with radius [radius], evenly by area, each a small soft dot. The
 * scatter is fixed by [seed] (the dab's index along the stroke), so a stroke redrawn the same way
 * sprays the same.
 */
fun sprayParticles(center: WorldPoint, radius: Double, seed: Int): List<WorldPoint> {
    val count = (SPRAY_DENSITY * radius * radius).toInt().coerceIn(3, SPRAY_MAX_PARTICLES)
    return List(count) { k ->
        val u = unitHash(seed, k * 2)
        val v = unitHash(seed, k * 2 + 1)
        // sqrt: uniform over the disc's area, not bunched at its centre.
        val r = radius * sqrt(u)
        val angle = 2 * PI * v
        WorldPoint(center.x + r * cos(angle), center.y + r * sin(angle))
    }
}

/** A spray particle's radius for a tip of [radius]: fine grain at any size, never under half a pixel. */
fun sprayParticleRadius(radius: Double, pixelWorld: Double): Double = maxOf(radius * SPRAY_PARTICLE_RADIUS, pixelWorld * 0.5)

/**
 * One dab of the bristle engine (roadmap item 140, Krita's hairy brush): [BRISTLE_HAIRS] hairs
 * spread across the tip, square to the stroke's direction ([from] to [to]), each painting a thin
 * line of its own. Each hair carries a little more or less paint ([seed]-fixed), and every hair
 * runs drier the further the stroke has gone ([travelled], world units), so marks streak and
 * fade like a real brush's. Returns each hair's position and how much paint (0..1) it lays.
 */
fun bristleHairs(from: WorldPoint, to: WorldPoint, radius: Double, travelled: Double, seed: Int): List<Pair<WorldPoint, Float>> {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    // Across the stroke; a dab with no direction yet lays its hairs horizontally.
    val nx = if (length > 1e-12) -dy / length else 0.0
    val ny = if (length > 1e-12) dx / length else 1.0
    val dryness = (travelled / (2 * radius).coerceAtLeast(1e-9) * BRISTLE_DRY_PER_WIDTH).coerceIn(0.0, 1.0)
    return List(BRISTLE_HAIRS) { k ->
        val across = (k + 0.5) / BRISTLE_HAIRS * 2 - 1 // -1..1
        val load = 0.55 + 0.45 * unitHash(seed, k) // some hairs hold more paint
        // Edge hairs run dry first.
        val paint = (load * (1 - dryness * (0.5 + 0.5 * kotlin.math.abs(across)))).coerceIn(0.0, 1.0)
        WorldPoint(to.x + nx * across * radius, to.y + ny * across * radius) to paint.toFloat()
    }
}

/** The width of one bristle's line for a tip of [radius]. */
fun bristleRadius(radius: Double, pixelWorld: Double): Double = maxOf(radius / BRISTLE_HAIRS, pixelWorld * 0.5)

/** A repeatable pseudo-random value in 0..1 for ([a], [b]). */
private fun unitHash(a: Int, b: Int): Double {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216.0
}
