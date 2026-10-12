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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Shape-tool rectangles are drawn with square corners, not rounded into blobs by stroke smoothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeRenderingTest {
    @Test
    fun rectangleCornersAreInked() {
        val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)
        val state = CanvasState()
        val stroke = Stroke(Color.Black, 4.0, PenType.MARKER)
        for (p in generateShapePoints(ShapeKind.RECTANGLE, WorldPoint(-100.0, -100.0), WorldPoint(100.0, 100.0))) stroke.addPoint(p)
        state.loadStrokes(listOf(stroke))
        val image = ImageBitmap(256, 256)
        val center = Offset(128f, 128f)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(256f, 256f)) {
            drawRect(Color.White)
            drawScene(state, center, state.screenToWorld(Offset.Zero, center), state.screenToWorld(Offset(256f, 256f), center), textMeasurer, { null })
        }
        val bitmap = image.asAndroidBitmap()
        // Just inside each corner (screen 28..228), where a smoothed path would leave a gap of ~30 px.
        for ((x, y) in listOf(29 to 29, 227 to 29, 227 to 227, 29 to 227)) {
            val pixel = bitmap.getPixel(x, y)
            assertTrue("corner ($x, $y) is ${Integer.toHexString(pixel)}", (pixel and 0xFF) < 0x40)
        }
    }
}
