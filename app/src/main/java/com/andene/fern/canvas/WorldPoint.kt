package com.andene.fern.canvas

/**
 * A point in world space, stored in Double precision.
 *
 * Compose's [androidx.compose.ui.geometry.Offset] is Float-only, which is nowhere near
 * enough range/precision for a truly infinite canvas: Float has ~7 significant decimal
 * digits, so a pan of a few million units already makes individual strokes jitter or
 * collapse to a single point. Double gives ~15-17 significant digits, and — combined with
 * the floating-origin rebasing in [CanvasState] — that precision is spent entirely on
 * *local* detail near wherever the camera currently is, not wasted representing large
 * absolute coordinates. That's what makes the zoom/pan range effectively unbounded for any
 * real drawing.
 */
data class WorldPoint(val x: Double, val y: Double) {
    operator fun plus(other: WorldPoint) = WorldPoint(x + other.x, y + other.y)
    operator fun minus(other: WorldPoint) = WorldPoint(x - other.x, y - other.y)
    operator fun times(factor: Double) = WorldPoint(x * factor, y * factor)
    operator fun div(factor: Double) = WorldPoint(x / factor, y / factor)

    companion object {
        val Zero = WorldPoint(0.0, 0.0)
    }
}
