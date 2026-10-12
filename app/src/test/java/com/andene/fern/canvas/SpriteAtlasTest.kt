package com.andene.fern.canvas

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.zip.ZipInputStream

/** Roadmap item 136: a trimmed, packed sprite atlas with a TexturePacker-style JSON. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpriteAtlasTest {
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)

    @Test
    fun packedRectsDontOverlapAndFitAPowerOfTwoSheet() {
        val sizes = listOf(100 to 40, 30 to 30, 64 to 64, 10 to 90, 50 to 20)
        val sheet = packRects(sizes)
        assertEquals(0, sheet.width and (sheet.width - 1))
        assertEquals(0, sheet.height and (sheet.height - 1))
        for (i in sizes.indices) {
            assertTrue(sheet.x[i] + sizes[i].first <= sheet.width && sheet.y[i] + sizes[i].second <= sheet.height)
            for (j in i + 1 until sizes.size) {
                val apart = sheet.x[i] + sizes[i].first <= sheet.x[j] || sheet.x[j] + sizes[j].first <= sheet.x[i] ||
                    sheet.y[i] + sizes[i].second <= sheet.y[j] || sheet.y[j] + sizes[j].second <= sheet.y[i]
                assertTrue("$i and $j overlap", apart)
            }
        }
    }

    @Test
    fun eachFrameIsTrimmedPackedAndTimed() {
        val state = CanvasState()
        state.loadStrokes(listOf(
            Stroke(Color.Red, 4.0, animFrame = 0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)) },
            Stroke(Color.Blue, 4.0, animFrame = 1).apply { addPoint(WorldPoint(50.0, 40.0)); addPoint(WorldPoint(60.0, 50.0)) },
        ))
        state.animFps = 10
        state.setHold(1, 3)
        val zip = exportSpriteAtlas(state, "walk", textMeasurer, Density(1f), { null })!!
        var json: JSONObject? = null
        var sheetSize = 0 to 0
        ZipInputStream(zip.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.name == "walk.json") json = JSONObject(input.readBytes().decodeToString())
                if (entry.name == "walk.png") BitmapFactory.decodeStream(input).let { sheetSize = it.width to it.height }
            }
        }
        val frames = json!!.getJSONObject("frames")
        assertEquals(setOf("walk_000.png", "walk_001.png"), frames.keys().asSequence().toSet())
        val second = frames.getJSONObject("walk_001.png")
        assertTrue(second.getBoolean("trimmed"))
        assertEquals(300L, second.getLong("duration")) // 10 fps, held 3 frames
        assertEquals(100L, frames.getJSONObject("walk_000.png").getLong("duration"))
        // The small second drawing sits right of and below the first's start in the full frame.
        assertTrue(second.getJSONObject("spriteSourceSize").getInt("x") > 0)
        val meta = json!!.getJSONObject("meta").getJSONObject("size")
        assertEquals(sheetSize, meta.getInt("w") to meta.getInt("h"))
    }
}
