package com.andene.fern

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Design tokens (design roadmap D1): the one source of every dimension, type size, radius and
 * duration in the UI. Every value sits on the CLAUDE.md §7 scale; UI code uses these tokens
 * instead of writing `N.dp` or `N.sp` itself, so the scale can't drift.
 */

/** The §7 scale itself, by value: primaries, secondaries (3, 6, 12…) and tertiaries (14, 30, 62…). */
object FernScale {
    val none: Dp = 0.dp
    val s1: Dp = 1.dp
    val s2: Dp = 2.dp
    val s3: Dp = 3.dp
    val s4: Dp = 4.dp
    val s6: Dp = 6.dp
    val s8: Dp = 8.dp
    val s12: Dp = 12.dp
    val s14: Dp = 14.dp
    val s16: Dp = 16.dp
    val s24: Dp = 24.dp
    val s30: Dp = 30.dp
    val s32: Dp = 32.dp
    val s48: Dp = 48.dp
    val s62: Dp = 62.dp
    val s64: Dp = 64.dp
    val s96: Dp = 96.dp
    val s126: Dp = 126.dp
    val s128: Dp = 128.dp
    val s192: Dp = 192.dp
    val s254: Dp = 254.dp
    val s256: Dp = 256.dp
    val s384: Dp = 384.dp
    val s510: Dp = 510.dp
    val s512: Dp = 512.dp
    val s768: Dp = 768.dp
    val s1024: Dp = 1024.dp

    /** Every value on the scale, smallest first (0 excluded). */
    val values: List<Int> = listOf(1, 2, 3, 4, 6, 8, 12, 14, 16, 24, 30, 32, 48, 62, 64, 96, 126, 128, 192, 254, 256, 384, 510, 512, 768, 1022, 1024)
}

/** Spacing between and inside elements. */
object FernSpace {
    val xxs = FernScale.s2
    val xs = FernScale.s4
    val sm = FernScale.s8
    val md = FernScale.s12
    val lg = FernScale.s16
    val xl = FernScale.s24
    val xxl = FernScale.s32
}

/** Sizes of recurring elements. */
object FernSize {
    val hairline = FernScale.s1
    val iconSmall = FernScale.s16
    val icon = FernScale.s24
    /** The smallest area a finger can reliably hit (Material, WCAG 2.5.8): every control's hit area. */
    val touchTarget = FernScale.s48
    /** A colour swatch as seen; its hit area is still [touchTarget]. */
    val swatch = FernScale.s24
    val numberField = FernScale.s96
    val dialogMaxHeight = FernScale.s512
}

/** Type sizes; nothing smaller than [caption] (design roadmap D5). */
object FernType {
    val caption: TextUnit = 12.sp
    val body: TextUnit = 16.sp
    val title: TextUnit = 24.sp
}

/** Animation durations in milliseconds (design roadmap D5 and D57). */
object FernMotion {
    const val SHORT = 128
    const val MEDIUM = 256
    const val LONG = 384
}

/** Corner radius for an element [height] tall: 0.24 × height, on the scale (CLAUDE.md §7.3). */
fun fernRadius(height: Dp): Dp = scaleNearest(height.value * 0.24f).dp

/**
 * [value] snapped to the scale (CLAUDE.md §7.1): the nearest value; on a tie, primary beats
 * secondary beats tertiary.
 */
fun scaleNearest(value: Float): Int {
    fun rank(v: Int) = when {
        v and (v - 1) == 0 -> 0 // a power of two: primary
        v in setOf(3, 6, 12, 24, 48, 96, 192, 384, 768) -> 1
        else -> 2
    }
    return FernScale.values.minWith(compareBy<Int> { kotlin.math.abs(it - value) }.thenBy { rank(it) })
}
