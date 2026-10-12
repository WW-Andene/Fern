package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Tiles must look like direct rendering; checked pixel by pixel on Robolectric's native Skia. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneTileCacheTest {
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)
    private val w = 640
    private val h = 480
    private val center = Offset(w / 2f, h / 2f)

    private fun scene(): CanvasState {
        val random = java.util.Random(3)
        val state = CanvasState()
        state.loadStrokes(List(400) {
            Stroke(Color(0xFF000000.toInt() or random.nextInt(0xFFFFFF)), 1.0 + random.nextInt(6), PenType.entries[random.nextInt(4)]).apply {
                var x = random.nextDouble() * 800 - 400
                var y = random.nextDouble() * 800 - 400
                var a = 0.0
                repeat(40) {
                    addPoint(WorldPoint(x, y), 0.5f + random.nextFloat() / 2)
                    a += random.nextDouble() - 0.5
                    x += Math.cos(a) * 8
                    y += Math.sin(a) * 8
                }
            }
        })
        state.loadTextItems(listOf(TextItem("t", "Across a tile boundary", WorldPoint(-60.0, 1.0), 24.0, Color.Black)))
        return state
    }

    private fun render(state: CanvasState, cache: SceneTileCache?): IntArray {
        val tl = state.screenToWorld(Offset.Zero, center)
        val br = state.screenToWorld(Offset(w.toFloat(), h.toFloat()), center)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawRect(state.backgroundColor)
            if (cache == null || !cache.draw(this, state, center, tl, br, textMeasurer) { null }) drawScene(state, center, tl, br, textMeasurer, { null })
        }
        return IntArray(w * h).also { image.asAndroidBitmap().getPixels(it, 0, w, 0, 0, w, h) }
    }

    /** Mean over pixels of the largest per-channel difference, and how many pixels differ by more than [threshold]. */
    private fun compare(a: IntArray, b: IntArray, threshold: Int): Pair<Double, Int> {
        var sum = 0L
        var over = 0
        for (i in a.indices) {
            var d = 0
            for (shift in intArrayOf(0, 8, 16)) d = maxOf(d, abs(((a[i] shr shift) and 255) - ((b[i] shr shift) and 255)))
            sum += d
            if (d > threshold) over++
        }
        return sum.toDouble() / a.size to over
    }

    @Test
    fun atAnOctaveScaleTilesMatchDirectRendering() {
        val state = scene()
        state.jumpTo(WorldPoint(13.0, -7.0), 1.0)
        val (mean, over) = compare(render(state, null), render(state, SceneTileCache()), 64)
        assertTrue("mean $mean", mean < 0.25)
        assertTrue("$over pixels differ visibly", over < 100) // anti-aliasing at tile seams only
    }

    @Test
    fun betweenOctavesTilesAreAResampledMatch() {
        val state = scene()
        state.jumpTo(WorldPoint(13.0, -7.0), 1.3)
        val (mean, _) = compare(render(state, null), render(state, SceneTileCache()), 64)
        assertTrue("mean $mean", mean < 16.0) // edge softening from the 1.3x scale-up, nothing displaced
    }

    @Test
    fun tilesAreReusedUntilTheSceneChanges() {
        val state = scene()
        state.jumpTo(WorldPoint(13.0, -7.0), 1.0)
        val cache = SceneTileCache()
        render(state, cache)
        val first = cache.tilesRendered
        assertTrue(first > 0)
        state.panByScreenDelta(Offset(30f, 0f)) // a small pan reuses almost every tile
        render(state, cache)
        assertTrue(cache.tilesRendered - first <= (h / TILE_PX + 2))
        val before = cache.tilesRendered
        render(state, cache)
        assertEquals(before, cache.tilesRendered) // unchanged view: nothing re-rendered
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(50.0, 50.0)); state.endStroke()
        render(state, cache)
        assertTrue(cache.tilesRendered > before) // the scene changed: tiles redrawn
    }

    @Test
    fun onlyLargeDocumentsWithNothingLiveUseTiles() {
        val state = scene()
        assertTrue(!state.tileCacheEligible) // 400 strokes: direct rendering
        state.loadStrokes(List(TILE_CACHE_MIN_STROKES) { i -> Stroke(Color.Black, 1.0).apply { addPoint(WorldPoint(i.toDouble(), 0.0)) } })
        assertTrue(state.tileCacheEligible)
        state.beginStroke(WorldPoint(0.0, 0.0))
        assertTrue(!state.tileCacheEligible) // drawing: live
        state.endStroke()
        state.selection.add(state.strokes.first())
        assertTrue(!state.tileCacheEligible)
    }
}
