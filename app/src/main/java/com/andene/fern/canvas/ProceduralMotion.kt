package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** The shape of a procedural sway (roadmap item 135). */
enum class SwayKind {
    /** An even back-and-forth: a tail wagging, a pendulum, hair in a steady breeze. */
    SINE,

    /** A smooth irregular wiggle that never quite repeats: idle fidgeting, a flame, a leaf. */
    WIGGLE,
}

/**
 * The turn (radians) a procedural sway adds in [frame]: at most [amplitude] either way, one
 * swing per [periodFrames] frames, starting [phase] (0..1 of a period) in. A wiggle is smooth
 * value noise - random heights each period, eased between - fixed by [seed], so regenerating
 * with the same settings gives the same motion. Frame 0 of a sine sway starts at the middle.
 */
fun proceduralSway(kind: SwayKind, frame: Int, amplitude: Double, periodFrames: Double, phase: Double = 0.0, seed: Int = 0): Double {
    val t = frame / periodFrames.coerceAtLeast(1e-9) + phase
    return when (kind) {
        SwayKind.SINE -> amplitude * sin(2 * PI * t)
        SwayKind.WIGGLE -> {
            val i = floor(t).toInt()
            val f = t - i
            val eased = f * f * (3 - 2 * f) // smoothstep: no kinks at the knots
            amplitude * (noiseAt(i, seed) * (1 - eased) + noiseAt(i + 1, seed) * eased)
        }
    }
}

/** A repeatable pseudo-random value in -1..1 for integer [i] (an integer hash). */
private fun noiseAt(i: Int, seed: Int): Double {
    var h = i * 374761393 + seed * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 32767.5 - 1.0
}
