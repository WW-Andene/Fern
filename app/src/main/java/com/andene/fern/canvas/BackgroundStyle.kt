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
