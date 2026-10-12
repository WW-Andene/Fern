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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Roadmap item 97: stroke caps, joins and width profiles - stored, and drawn. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LineStyleTest {
    private fun line(style: LineStyle?) = Stroke(Color.Black, 16.0, lineStyle = style).apply {
        for (x in 0..20) addPoint(WorldPoint(-100.0 + x * 10.0, 0.0))
    }

    @Test
    fun styleSurvivesTheStrokeFormat() {
        val style = LineStyle(LineCap.BUTT, LineJoin.BEVEL, WidthProfile.TAPER_END)
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), listOf(line(style), line(null))) }.toByteArray()
        val back = StrokeCodec.read(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(style, back[0].lineStyle)
        assertEquals(null, back[1].lineStyle)
    }

    @Test
    fun profilesTaperTheirEnds() {
        assertEquals(1f, WidthProfile.TAPER_BOTH.at(0.5f), 1e-6f)
        assertEquals(0.1f, WidthProfile.TAPER_BOTH.at(0f), 1e-6f)
        assertEquals(1f, WidthProfile.TAPER_START.at(1f), 1e-6f)
        assertEquals(0.1f, WidthProfile.TAPER_END.at(1f), 1e-6f)
    }

    /** Ink darkness (0 white .. 255 black) at screen pixels, for one stroke drawn centred in a 256 px view. */
    private fun inkAt(stroke: Stroke, vararg pixels: Pair<Int, Int>): List<Int> {
        val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)
        val state = CanvasState()
        state.loadStrokes(listOf(stroke))
        val image = ImageBitmap(256, 256)
        val center = Offset(128f, 128f)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(256f, 256f)) {
            drawRect(Color.White)
            drawScene(state, center, state.screenToWorld(Offset.Zero, center), state.screenToWorld(Offset(256f, 256f), center), textMeasurer, { null })
        }
        val bitmap = image.asAndroidBitmap()
        return pixels.map { (x, y) -> 255 - (bitmap.getPixel(x, y) and 0xFF) }
    }

    @Test
    fun aTaperedLineIsThinAtItsEndsAndFullInTheMiddle() {
        // The line runs from screen x 28 to 228 at y 128, 16 px wide; 6 px off the centre line:
        val (middle, nearEnd) = inkAt(line(LineStyle(profile = WidthProfile.TAPER_BOTH)), 128 to 134, 32 to 134)
        assertTrue("middle $middle", middle > 200)
        assertTrue("near the end $nearEnd", nearEnd < 40)
        val (uniformNearEnd) = inkAt(line(null), 32 to 134)
        assertTrue("uniform near the end $uniformNearEnd", uniformNearEnd > 200)
    }

    @Test
    fun aFlatCapStopsAtTheEndWhereARoundOneReachesPast() {
        val (flat) = inkAt(line(LineStyle(cap = LineCap.BUTT)), 22 to 128)
        val (round) = inkAt(line(LineStyle(cap = LineCap.ROUND)), 22 to 128)
        assertTrue("flat $flat", flat < 40)
        assertTrue("round $round", round > 200)
    }
}
