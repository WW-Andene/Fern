package com.andene.fern.canvas

import com.andene.fern.R
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign

/**
 * Typeface of a text label. The platform's generic families, so no font files ship with the
 * app and every device has all four.
 */
enum class TextFont(@StringRes val label: Int, val fontFamily: FontFamily, val svgFamily: String) {
    SANS(R.string.font_sans, FontFamily.SansSerif, "sans-serif"),
    SERIF(R.string.font_serif, FontFamily.Serif, "serif"),
    MONO(R.string.font_mono, FontFamily.Monospace, "monospace"),
    HANDWRITING(R.string.font_handwriting, FontFamily.Cursive, "cursive"),
}

/**
 * OpenType features a label can turn on or off (roadmap item 98), by their four-letter tags.
 * Fonts without a feature ignore it, as in any app; the platform's generic families support
 * ligatures, figure styles and fractions, while small caps depend on the device's fonts.
 */
enum class OpenTypeFeature(@StringRes val label: Int, val setting: String) {
    NO_LIGATURES(R.string.otf_no_ligatures, "liga 0, clig 0"),
    SMALL_CAPS(R.string.otf_small_caps, "smcp"),
    OLDSTYLE_FIGURES(R.string.otf_oldstyle, "onum"),
    TABULAR_FIGURES(R.string.otf_tabular, "tnum"),
    FRACTIONS(R.string.otf_fractions, "frac"),
}

/** [features] as a CSS-style feature setting string ("smcp, tnum"), or null for none. */
fun featureSettings(features: Set<OpenTypeFeature>): String? =
    features.takeIf { it.isNotEmpty() }?.sortedBy { it.ordinal }?.joinToString(", ") { it.setting }

/**
 * Where a linked label leads (roadmap item 46): a named pin in the same document (a region of
 * the canvas), or another document. Stored by id, so renaming the target keeps the link; a
 * target that was deleted is reported when the link is followed.
 */
sealed interface LinkTarget {
    data class Pin(val pinId: String) : LinkTarget
    data class Document(val documentId: String) : LinkTarget
}

/** What a label can link to, offered in the text dialog. */
data class LinkChoices(val pins: List<PinMeta> = emptyList(), val documents: List<DocumentMeta> = emptyList())

/** Horizontal alignment of a label's lines: within its box if it has one, else against its longest line. */
enum class TextAlignment(@StringRes val label: Int, val textAlign: TextAlign, val svgAnchor: String) {
    START(R.string.align_text_start, TextAlign.Start, "start"),
    CENTER(R.string.align_text_center, TextAlign.Center, "middle"),
    END(R.string.align_text_end, TextAlign.End, "end"),
}

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
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** True for a sticky note: drawn on a yellow note behind the text. */
    val sticky: Boolean = false,
    val font: TextFont = TextFont.SANS,
    val alignment: TextAlignment = TextAlignment.START,
    /**
     * Width of the label's text box in world units, or null for a label without one (lines only
     * break where the text has newlines). Text in a box wraps to its width. World units, like
     * [fontSizeWorld], so the box zooms with the canvas.
     */
    val boxWidthWorld: Double? = null,
    /** Where tapping "Follow link" on this label leads; null for an unlinked label. */
    val link: LinkTarget? = null,
    /** Drawn as letter outlines instead of solid letters (roadmap item 98). */
    val outlined: Boolean = false,
    /**
     * Text on a path (roadmap item 98): the line the text follows, relative to [position] (so
     * moving the label moves its path). Null for ordinary text. The text runs along it from its
     * start, one line, each letter turned to the path's direction.
     */
    val path: List<WorldPoint>? = null,
    /** OpenType features on for this label (roadmap item 98). */
    val features: Set<OpenTypeFeature> = emptySet(),
)
