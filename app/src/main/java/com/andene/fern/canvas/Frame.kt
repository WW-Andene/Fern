package com.andene.fern.canvas

/**
 * A named rectangular region of the infinite canvas (an artboard): something to jump to and
 * to export as its own image or PDF page. Stored in world space like everything else, and
 * shifted along with it on a floating-origin rebase.
 */
data class Frame(
    val id: String,
    val name: String,
    val min: WorldPoint,
    val max: WorldPoint,
)
