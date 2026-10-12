package com.andene.fern.canvas

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
enum class DocumentTemplate(val label: String, val paper: BackgroundStyle) {
    BLANK("Blank", BackgroundStyle.PLAIN),
    GRID("Graph paper", BackgroundStyle.GRID),
    DOT("Dot grid", BackgroundStyle.DOT),
    RULED("Lined notes", BackgroundStyle.RULED),
    COMIC("Comic page", BackgroundStyle.PLAIN),
    STORYBOARD("Storyboard", BackgroundStyle.PLAIN),
}
