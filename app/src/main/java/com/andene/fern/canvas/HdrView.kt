package com.andene.fern.canvas

import kotlin.math.pow

/**
 * HDR layers (roadmap item 139) store sRGB-encoded values divided by this, so light up to this
 * encoded level - linear [HDR_MAX_LINEAR], about 22 times paper white - fits the 16-bit range
 * and paint mixes exactly as on any layer.
 */
const val HDR_ENCODED_MAX = 4f

/** The brightest linear light an HDR layer holds: [HDR_ENCODED_MAX] decoded from sRGB. */
val HDR_MAX_LINEAR: Float = srgbToLinear(HDR_ENCODED_MAX)

/** How an HDR layer's light past paper white is shown on an ordinary screen. */
enum class ToneMap {
    /** Everything past white shows as white. */
    CLIP,

    /** A filmic curve (Narkowicz's fit of the ACES reference rendering): highlights roll off instead of clipping. */
    FILMIC,
}

/**
 * The view transform for HDR layers (roadmap item 139), standing in for an OpenColorIO display
 * transform: [exposureStops] brightens or darkens the light (each stop doubles it), then
 * [toneMap] brings it into the screen's range. It only changes what's shown, never the pixels.
 */
data class HdrView(val exposureStops: Float = 0f, val toneMap: ToneMap = ToneMap.CLIP) {
    /** Linear light [linear] after exposure and tone mapping, 0..1 for the screen. */
    fun display(linear: Float): Float {
        val exposed = linear * 2f.pow(exposureStops)
        return when (toneMap) {
            ToneMap.CLIP -> exposed
            ToneMap.FILMIC -> {
                val x = exposed * FILMIC_INPUT_SCALE
                (x * (2.51f * x + 0.03f)) / (x * (2.43f * x + 0.59f) + 0.14f)
            }
        }.coerceIn(0f, 1f)
    }
}

// The ACES fit maps scene light 1.0 to about 0.8; scaled so paper white still shows near white.
private const val FILMIC_INPUT_SCALE = 1.6f
