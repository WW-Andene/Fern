package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 137: wrap-around tiled mode for seamless patterns. */
class WrapAroundTest {
    private val tile = Frame("t", WRAP_TILE_FRAME_NAME, WorldPoint(0.0, 0.0), WorldPoint(100.0, 100.0))

    @Test
    fun offsetsCoverTheViewInWholeTiles() {
        val offsets = wrapOffsets(tile, WorldPoint(-50.0, 10.0), WorldPoint(150.0, 90.0))
        assertTrue(WorldPoint.Zero in offsets)
        assertTrue(WorldPoint(-100.0, 0.0) in offsets && WorldPoint(100.0, 0.0) in offsets)
        assertTrue(offsets.all { it.x % 100.0 == 0.0 && it.y % 100.0 == 0.0 })
        // Zoomed far out, the copies are capped.
        assertTrue(wrapOffsets(tile, WorldPoint(-1e6, -1e6), WorldPoint(1e6, 1e6)).size <= WRAP_MAX_REPEATS * WRAP_MAX_REPEATS)
    }

    @Test
    fun inkDrawnOnARepeatMovesOntoTheTile() {
        assertEquals(WorldPoint.Zero, wrappedIntoTile(tile, WorldPoint(50.0, 50.0)))
        assertEquals(WorldPoint(200.0, -100.0), wrappedIntoTile(tile, WorldPoint(250.0, -50.0)))

        val state = CanvasState()
        state.viewportSize = Size(400f, 300f)
        state.toggleWrapAround()
        val made = state.wrapTile!!
        assertEquals(WRAP_TILE_FRAME_NAME, made.name)
        assertEquals(made.max.x - made.min.x, made.max.y - made.min.y, 1e-9) // square
        val w = made.max.x - made.min.x
        val start = WorldPoint(made.min.x + 2 * w + 1.0, made.min.y + 1.0) // two tiles to the right
        state.beginStroke(start); state.extendStroke(start + WorldPoint(5.0, 5.0)); state.endStroke()
        assertEquals(made.min.x + 1.0, state.strokes.single().points.first().x, 1e-9)
        state.toggleWrapAround()
        assertNull(state.wrapTile)
        state.toggleWrapAround() // the "Tile" frame is reused
        assertEquals(made.id, state.wrapTile!!.id)
        assertEquals(1, state.frames.size)
    }
}
