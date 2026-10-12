package com.andene.fern.canvas

import androidx.annotation.StringRes
import com.andene.fern.R

/** How a stroke's ends are drawn (roadmap item 97). */
enum class LineCap(@StringRes val label: Int) { ROUND(R.string.cap_round), SQUARE(R.string.cap_square), BUTT(R.string.cap_butt) }

/** How a stroke's bends are drawn, for uniform-width strokes (roadmap item 97). */
enum class LineJoin(@StringRes val label: Int) { ROUND(R.string.join_round), MITER(R.string.join_miter), BEVEL(R.string.join_bevel) }

/** How a stroke's width runs along it (roadmap item 97): even, or tapering to a point at one or both ends. */
enum class WidthProfile(@StringRes val label: Int) {
    UNIFORM(R.string.profile_uniform),
    TAPER_BOTH(R.string.profile_taper_both),
    TAPER_START(R.string.profile_taper_start),
    TAPER_END(R.string.profile_taper_end);

    /**
     * Width multiplier at fraction [t] (0..1) of the stroke's length: tapers run over the first
     * or last [TAPER_LENGTH] of it, easing (smoothstep) from [TAPER_MIN] to full width.
     */
    fun at(t: Float): Float {
        fun ease(x: Float): Float {
            val u = (x / TAPER_LENGTH).coerceIn(0f, 1f)
            return TAPER_MIN + (1 - TAPER_MIN) * u * u * (3 - 2 * u)
        }
        return when (this) {
            UNIFORM -> 1f
            TAPER_BOTH -> minOf(ease(t), ease(1 - t))
            TAPER_START -> ease(t)
            TAPER_END -> ease(1 - t)
        }
    }

    private companion object {
        const val TAPER_LENGTH = 0.25f
        const val TAPER_MIN = 0.1f
    }
}

/** A stroke's line style; strokes without one draw with their pen's defaults. */
data class LineStyle(val cap: LineCap = LineCap.ROUND, val join: LineJoin = LineJoin.ROUND, val profile: WidthProfile = WidthProfile.UNIFORM) {
    /** Packed into one byte for [StrokeCodec]: cap in bits 0-1, join in bits 2-3, profile in bits 4-6. */
    fun pack(): Int = cap.ordinal or (join.ordinal shl 2) or (profile.ordinal shl 4)

    companion object {
        /** Unknown values (from a newer Fern) fall back to the defaults. */
        fun unpack(bits: Int) = LineStyle(
            LineCap.entries.getOrElse(bits and 3) { LineCap.ROUND },
            LineJoin.entries.getOrElse((bits shr 2) and 3) { LineJoin.ROUND },
            WidthProfile.entries.getOrElse((bits shr 4) and 7) { WidthProfile.UNIFORM },
        )
    }
}
