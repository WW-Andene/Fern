package com.andene.fern.canvas

/**
 * A placed image on the canvas. [position] is the image's top-left corner in world space;
 * [widthWorld]/[heightWorld] are its displayed size, following the same scale-dependent
 * convention as [Stroke.widthWorld] (fixed screen-pixel-equivalent size divided by the scale
 * in effect when placed).
 *
 * [fileName] names the decoded copy under the app's private `images/` directory (shared
 * across all documents, keyed by this id - see [CanvasStorage]). Duplicating a document
 * copies the metadata entry, not the file, since the file is immutable once written.
 */
data class ImageItem(
    val id: String,
    val fileName: String,
    val position: WorldPoint,
    val widthWorld: Double,
    val heightWorld: Double,
)
