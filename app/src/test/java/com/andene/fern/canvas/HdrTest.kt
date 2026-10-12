package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
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

/** Roadmap item 139: HDR layers, their view transform, and OpenEXR keeping light past white. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HdrTest {
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)

    @Test
    fun theViewTransformExposesAndRollsOffHighlights() {
        assertEquals(1f, HdrView().display(2f), 0f) // clipped
        assertEquals(0.5f, HdrView(exposureStops = 1f).display(0.25f), 1e-6f)
        val filmic = HdrView(toneMap = ToneMap.FILMIC)
        // Rolls off instead of clipping: four times white still below white, and brighter than white.
        assertTrue(filmic.display(4f) < 1f && filmic.display(4f) > filmic.display(1f))
        assertTrue(filmic.display(1f) > 0.75f) // paper white still reads near white
    }

    @Test
    fun theHdrPenPaintsBrighterThanWhite() {
        val state = CanvasState()
        state.addRasterLayer(8, hdr = true)
        val layer = state.layers.last()
        assertTrue(layer.hdr && layer.depth == 16)
        state.activeColor = Color.White
        state.hdrIntensity = 4f
        state.activeWidthWorld = 16.0
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(20.0, 0.0)); state.endStroke()
        val stored = state.rasters.getValue(layer.id).pixel(10, 0)
        val light = srgbToLinear(stored[0] / stored[3] * HDR_ENCODED_MAX)
        assertEquals(4f, light, 0.05f)
    }

    @Test
    fun anHdrExrComesInAsAnHdrLayerAndGoesBackOutBright() {
        val bright = ExrImage(4, 4, FloatArray(4 * 4 * 4) { if (it % 4 == 3) 1f else 3f }) // three times paper white
        val document = OpenExr.read(OpenExr.write(bright)).toImportedDocument("sky")
        assertTrue(document.hdr)
        val state = CanvasState()
        state.importLayers(document)
        assertTrue(state.layers.last().hdr)
        val out = OpenExr.read(exportDocumentExr(state, textMeasurer, Density(1f), { null })!!)
        val brightest = (0 until out.width * out.height).maxOf { out.rgba[it * 4] }
        assertEquals(3f, brightest, 0.05f)
    }
}
