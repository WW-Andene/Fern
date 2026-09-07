package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color

/**
 * A single placed text label on the canvas. [position] is the label's top-left corner in
 * world space; [fontSizeWorld] follows the same scale-dependent convention as
 * [Stroke.widthWorld] (a fixed screen-pixel size divided by the scale in effect when it was
 * placed, so it stays a fixed visual size relative to its surroundings as you zoom).
 *
 * Immutable/copy-on-edit (like most small value types here) rather than mutable fields, since
 * [CanvasState.textItems] is a `mutableStateListOf` - replacing an element is how Compose
 * observes an edit; mutating a field on an existing instance wouldn't trigger recomposition.
 */
data class TextItem(
    val id: String,
    val text: String,
    val position: WorldPoint,
    val fontSizeWorld: Double,
    val color: Color,
)
