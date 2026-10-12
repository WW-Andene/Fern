package com.andene.fern.canvas

import androidx.annotation.StringRes
import com.andene.fern.R

/**
 * Timing curve for generated motion (roadmap item 81): how far along the change from one key
 * frame to the next each in-between, or each motion-path step, lands. [at] maps time 0..1 to
 * progress 0..1, with 0 and 1 fixed.
 */
enum class Easing(@StringRes val label: Int) {
    LINEAR(R.string.easing_linear),
    /** Starts slowly, speeds up (t²). */
    EASE_IN(R.string.easing_in),
    /** Starts quickly, slows into the next key (1 - (1 - t)²). */
    EASE_OUT(R.string.easing_out),
    /** Slow at both keys, quickest midway (smoothstep, 3t² - 2t³). */
    EASE_IN_OUT(R.string.easing_in_out),
    /** A curve the user shapes in the curve editor ([CubicEasing]); see [CanvasState.easedProgress]. */
    CUSTOM(R.string.easing_custom);

    /** Progress at time [t]; [CUSTOM] needs its curve, so on its own it's linear. */
    fun at(t: Double): Double = when (this) {
        LINEAR, CUSTOM -> t
        EASE_IN -> t * t
        EASE_OUT -> 1 - (1 - t) * (1 - t)
        EASE_IN_OUT -> t * t * (3 - 2 * t)
    }
}

/**
 * A timing curve drawn as a cubic Bézier from (0, 0) to (1, 1) with two handles at ([x1], [y1])
 * and ([x2], [y2]) - the convention of CSS `cubic-bezier()` and most animation tools' graph
 * editors. Handle times stay in 0..1 so the curve is a function of time; progress may overshoot
 * (below 0 or above 1) for anticipation and bounce-back.
 */
data class CubicEasing(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    init {
        require(x1 in 0.0..1.0 && x2 in 0.0..1.0) { "Handle times must be within 0..1" }
    }

    /** Progress at time [t]: finds the curve parameter whose x is [t] (bisection, monotonic in x), then its y. */
    fun at(t: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return 1.0
        var low = 0.0
        var high = 1.0
        var s = t
        repeat(SOLVE_STEPS) {
            s = (low + high) / 2
            if (bezier(s, x1, x2) < t) low = s else high = s
        }
        return bezier(s, y1, y2)
    }

    companion object {
        // 40 halvings: far below a frame's worth of precision.
        private const val SOLVE_STEPS = 40

        /** The CSS "ease-in-out" curve, where the editor starts. */
        val DEFAULT = CubicEasing(0.42, 0.0, 0.58, 1.0)

        /** One coordinate of the curve with end points 0 and 1 and handles [a], [b] at parameter [s]. */
        fun bezier(s: Double, a: Double, b: Double): Double {
            val u = 1 - s
            return 3 * u * u * s * a + 3 * u * s * s * b + s * s * s
        }
    }
}
