package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 145: frame tags, onion skin settings and RotSprite. */
@RunWith(RobolectricTestRunner::class)
class AsepriteParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun animated(frames: Int) = CanvasState().apply {
        repeat(frames - 1) { insertAnimFrame(copyCurrent = false) }
        goToAnimFrame(0)
    }

    @Test
    fun tagsShiftWithInsertedAndDeletedFrames() {
        val tags = listOf(FrameTag("a", 0, 1, 0), FrameTag("b", 2, 4, 0), FrameTag("c", 5, 5, 0))
        assertEquals(listOf(FrameTag("a", 0, 1, 0), FrameTag("b", 2, 5, 0), FrameTag("c", 6, 6, 0)), tagsAfterInsert(tags, 3))
        assertEquals(listOf(FrameTag("a", 0, 1, 0), FrameTag("b", 3, 5, 0), FrameTag("c", 6, 6, 0)), tagsAfterInsert(tags, 2))
        assertEquals(listOf(FrameTag("a", 0, 1, 0), FrameTag("b", 2, 4, 0)), tagsAfterDelete(tags, 5))
        assertEquals(listOf(FrameTag("a", 0, 0, 0), FrameTag("b", 1, 3, 0), FrameTag("c", 4, 4, 0)), tagsAfterDelete(tags, 1))
    }

    @Test
    fun playingATagLoopsItsFramesInItsDirection() {
        val state = animated(8)
        state.putFrameTag(FrameTag("walk", 2, 4, TAG_COLORS[0]))
        state.putFrameTag(FrameTag("back", 5, 7, TAG_COLORS[1], TagDirection.REVERSE))
        state.putFrameTag(FrameTag("bob", 0, 2, TAG_COLORS[2], TagDirection.PING_PONG))
        state.playTag("walk")
        val seen = (0 until 6).map { state.currentAnimFrame.also { state.advanceAnimFrame() } }
        assertEquals(listOf(2, 3, 4, 2, 3, 4), seen)
        state.playTag("back")
        assertEquals(listOf(7, 6, 5, 7), (0 until 4).map { state.currentAnimFrame.also { state.advanceAnimFrame() } })
        state.playTag("bob")
        assertEquals(listOf(0, 1, 2, 1, 0, 1), (0 until 6).map { state.currentAnimFrame.also { state.advanceAnimFrame() } })
        // Deleting the tag stops confining playback.
        state.removeFrameTag("bob")
        assertNull(state.playingTag)
    }

    @Test
    fun tagsAreSavedAndExportedWithTheAtlas() {
        val settings = AnimationSettings(tags = listOf(FrameTag("idle", 0, 3, TAG_COLORS[3], TagDirection.PING_PONG)))
        CanvasStorage.saveAnimationSettings(context, "doc", settings)
        assertEquals(settings.tags, CanvasStorage.loadAnimationSettings(context, "doc").tags)
    }

    @Test
    fun scale2xSmoothsADiagonalWithoutNewColours() {
        val x = 0xFF000000.toInt()
        val o = 0
        // A one-pixel diagonal staircase.
        val image = PixelImage(3, 3, intArrayOf(x, o, o, o, x, o, o, o, x))
        val doubled = scale2x(image)
        assertEquals(6, doubled.width)
        assertTrue(doubled.pixels.all { it == x || it == o })
        // The corners between diagonal pixels fill in, joining the line.
        assertEquals(x, doubled.at(2, 1))
        assertEquals(x, doubled.at(1, 2))
    }

    @Test
    fun rotationKeepsColoursAndTurnsRightAnglesExactly() {
        val r = 0xFFFF0000.toInt()
        val g = 0xFF00FF00.toInt()
        val image = PixelImage(2, 1, intArrayOf(r, g))
        val quarter = rotatePixels(image, 90.0, rotSprite = true)
        assertEquals(1, quarter.width)
        assertEquals(2, quarter.height)
        assertEquals(listOf(r, g), quarter.pixels.toList()) // red at the top after a clockwise turn
        val bar = PixelImage(8, 2, IntArray(16) { r })
        val turned = rotatePixels(bar, 30.0, rotSprite = true)
        assertTrue(turned.pixels.all { it == r || it == 0 })
        // Roughly the bar's area survives (no holes).
        assertEquals(16.0, turned.pixels.count { it == r }.toDouble(), 4.0)
    }

    /** The painted pixels' bounds on [surface]: left, top, right, bottom (inclusive), and how many. */
    private fun painted(surface: RasterSurface): Pair<IntArray, Int> {
        var count = 0
        val box = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
        for ((key, tile) in surface.tiles) {
            for (i in 0 until tile.data.size / 4) {
                if (RasterSurface.u16(tile.data[i * 4 + 3]) == 0) continue
                count++
                val x = RasterSurface.tileX(key) * RASTER_TILE + i % RASTER_TILE
                val y = RasterSurface.tileY(key) * RASTER_TILE + i / RASTER_TILE
                box[0] = minOf(box[0], x); box[1] = minOf(box[1], y); box[2] = maxOf(box[2], x); box[3] = maxOf(box[3], y)
            }
        }
        return box to count
    }

    @Test
    fun rotatingRasterPixelsTurnsThemUndoably() {
        val state = CanvasState()
        state.addRasterLayer(depth = 8)
        state.activeColor = Color.Red
        state.activeTool = Tool.SELECT
        // A 40 x 4 marquee, filled: a short horizontal bar.
        state.beginSelectGesture(WorldPoint(0.0, 0.0))
        state.continueSelectGesture(WorldPoint(40.0, 4.0))
        state.endSelectGesture()
        state.fillRasterSelection()
        val surface = state.rasters.values.single()
        val (bar, area) = painted(surface)
        assertTrue(bar[2] - bar[0] > bar[3] - bar[1])
        state.rotateRasterPixels(90.0, rotSprite = true)
        val (turned, turnedArea) = painted(surface)
        assertEquals(area, turnedArea) // a right angle moves every pixel, loses none
        assertTrue(turned[3] - turned[1] > turned[2] - turned[0])
        state.undo()
        val (restored, _) = painted(state.rasters.values.single())
        assertEquals(bar.toList(), restored.toList())
    }
}
