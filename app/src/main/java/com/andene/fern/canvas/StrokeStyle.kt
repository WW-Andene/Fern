package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color

/**
 * Everything about how a stroke looks, apart from its geometry and where it lives (layer,
 * frame, group, bone): what the style picker (roadmap item 121) copies from one object and
 * pastes onto others, the way Affinity Designer's Style Picker does.
 */
data class StrokeStyle(
    /** Copied from a fill: only its fill attributes mean anything (and an outline's only its outline ones). */
    val fromFill: Boolean,
    val color: Color,
    val widthWorld: Double,
    val penType: PenType,
    val blendMode: StrokeBlendMode,
    val dashed: Boolean,
    val lineStyle: LineStyle?,
    val brush: StampBrush?,
    val fadeFill: Boolean,
    val screentone: Boolean,
    val gradient: FillGradient?,
) {
    companion object {
        fun of(stroke: Stroke) = StrokeStyle(
            stroke.filled,
            stroke.color, stroke.widthWorld, stroke.penType, stroke.blendMode, stroke.dashed,
            stroke.lineStyle, stroke.brush, stroke.fadeFill, stroke.screentone, stroke.gradient,
        )
    }
}
