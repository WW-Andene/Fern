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
    EASE_IN_OUT(R.string.easing_in_out);

    fun at(t: Double): Double = when (this) {
        LINEAR -> t
        EASE_IN -> t * t
        EASE_OUT -> 1 - (1 - t) * (1 - t)
        EASE_IN_OUT -> t * t * (3 - 2 * t)
    }
}
