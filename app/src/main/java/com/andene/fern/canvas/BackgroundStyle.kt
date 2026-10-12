package com.andene.fern.canvas

import com.andene.fern.R
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The page pattern drawn behind a document's content, matching common physical paper styles:
 * - [PLAIN]: a flat color, no pattern.
 * - [GRID]: evenly spaced horizontal and vertical lines, like graph paper.
 * - [DOT]: dots at the same spacing as [GRID], like dot-grid paper.
 * - [RULED]: evenly spaced horizontal lines only, like lined notebook paper.
 *
 * Drawn in world space at a fixed world-unit spacing (see `DrawingCanvas`'s
 * `BACKGROUND_PATTERN_SPACING_WORLD`), so the pattern pans and zooms together with the
 * content sitting on it, the way a real sheet of patterned paper would.
 */
enum class BackgroundStyle { PLAIN, GRID, DOT, RULED }

/**
 * World-unit spacing between pattern lines/dots - the page pattern's own fixed "grain",
 * independent of stroke width or any other scale-dependent value in this app. Shared with
 * [CanvasState]'s origin rebase and grid snapping, which must stay aligned with the pattern.
 */
const val BACKGROUND_PATTERN_SPACING_WORLD = 40.0

/** Starting points offered when creating a document: a paper style, optionally with a page layout. */
enum class DocumentTemplate(@StringRes val label: Int, val paper: BackgroundStyle) {
    BLANK(R.string.template_blank, BackgroundStyle.PLAIN),
    GRID(R.string.template_grid, BackgroundStyle.GRID),
    DOT(R.string.template_dot, BackgroundStyle.DOT),
    RULED(R.string.template_ruled, BackgroundStyle.RULED),
    COMIC(R.string.frames_comic, BackgroundStyle.PLAIN),
    STORYBOARD(R.string.template_storyboard, BackgroundStyle.PLAIN),
    PLANNER(R.string.template_planner, BackgroundStyle.PLAIN),
    MIND_MAP(R.string.template_mind_map, BackgroundStyle.PLAIN),
}

/** Paper colours offered as presets, each with an ink that reads well on it (roadmap item 73). */
enum class PaperPreset(@StringRes val label: Int, val background: Color, val ink: Color) {
    LIGHT(R.string.paper_light, Color(0xFFF7F5F0), Color.Black),
    DARK(R.string.paper_dark, Color(0xFF202124), Color.White),
}

/** WCAG contrast ratio between two colours (alpha ignored), from 1 to 21. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.copy(alpha = 1f).luminance() + 0.05
    val lb = b.copy(alpha = 1f).luminance() + 0.05
    return (maxOf(la, lb) / minOf(la, lb)).toDouble()
}
