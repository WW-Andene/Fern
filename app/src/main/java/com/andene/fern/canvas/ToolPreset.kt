package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color

/** Number of favourite tool slots on the toolbar. */
const val TOOL_SLOT_COUNT = 4

/**
 * A saved pen setup for a favourite slot: everything that decides how the next pen stroke
 * looks, so recalling a slot switches pen type, colour, width, opacity, blend mode, dashes and
 * stamp brush in one tap.
 */
data class ToolPreset(
    val penType: PenType,
    val color: Color,
    /** The width setting, in the same units as [CanvasState.activeWidthWorld]. */
    val width: Double,
    val opacity: Double,
    val blendMode: StrokeBlendMode,
    val dashed: Boolean,
    val brush: StampBrush,
    /** The line's dashes and arrowheads; kept by per-tool memory (design roadmap D81), not saved with a slot. */
    val lineStyle: LineStyle = LineStyle(),
)
