package com.andene.fern.canvas

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.zip.ZipInputStream

/** Roadmap item 128: every frame as slices at 1x, @2x and @3x. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SliceExportTest {
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)

    @Test
    fun namesGetScaleSuffixesAndStayUnique() {
        val frames = listOf(Frame("1", "Icon/large", WorldPoint.Zero, WorldPoint(10.0, 10.0)), Frame("2", "icon_large", WorldPoint.Zero, WorldPoint(10.0, 10.0)))
        assertEquals(
            listOf("Icon_large.png", "Icon_large@2x.png", "icon_large 2.png", "icon_large 2@2x.png"),
            sliceFileNames(frames, listOf(2, 1)).map { it.third },
        )
    }

    @Test
    fun eachSliceIsItsFrameAtItsScale() {
        val state = CanvasState()
        state.loadStrokes(listOf(Stroke(Color.Red, 4.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 50.0)) }))
        val frames = listOf(Frame("1", "Badge", WorldPoint(0.0, 0.0), WorldPoint(40.0, 20.0)))
        val zip = exportSlicesZip(state, frames, listOf(1, 3), textMeasurer, Density(1f), { null })!!
        val sizes = LinkedHashMap<String, Pair<Int, Int>>()
        ZipInputStream(zip.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val bitmap = BitmapFactory.decodeStream(input)
                sizes[entry.name] = bitmap.width to bitmap.height
            }
        }
        assertEquals(mapOf("Badge.png" to (40 to 20), "Badge@3x.png" to (120 to 60)), sizes)
        assertNull(exportSlicesZip(state, emptyList(), listOf(1), textMeasurer, Density(1f), { null }))
    }
}
