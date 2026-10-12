package com.andene.fern.canvas

import android.content.Context
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Roadmap item 93: Photoshop blend modes and adjustment layers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayerBlendTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cpuModesFollowTheirFormulas() {
        val grey = floatArrayOf(0.6f, 0.6f, 0.6f, 1f)
        assertEquals(0.2f, blendPixel(LayerBlend.LINEAR_BURN, grey, grey)[0], 1e-4f)
        assertEquals(0.0f, blendPixel(LayerBlend.SUBTRACT, grey, grey)[0], 1e-4f)
        assertEquals(1.0f, blendPixel(LayerBlend.DIVIDE, grey, grey)[0], 1e-4f)
        assertEquals(0.8f, blendPixel(LayerBlend.LINEAR_LIGHT, grey, grey)[0], 1e-4f)
        assertEquals(1.0f, blendPixel(LayerBlend.HARD_MIX, grey, grey)[0], 1e-4f)
        // Half-transparent source: half the blend effect.
        assertEquals(0.3f, blendPixel(LayerBlend.SUBTRACT, grey, floatArrayOf(0.6f, 0.6f, 0.6f, 0.5f))[0], 1e-4f)
    }

    private fun render(state: CanvasState): android.graphics.Bitmap {
        val textMeasurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        val image = ImageBitmap(64, 64)
        val center = Offset(32f, 32f)
        state.jumpTo(WorldPoint(32.0, 32.0), 1.0)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(64f, 64f)) {
            drawRect(Color.White)
            drawScene(state, center, state.screenToWorld(Offset.Zero, center), state.screenToWorld(Offset(64f, 64f), center), textMeasurer, { null })
        }
        return image.asAndroidBitmap()
    }

    private fun filled(color: Color) = Stroke(color, 1.0, filled = true).apply {
        for (p in listOf(WorldPoint(-10.0, -10.0), WorldPoint(80.0, -10.0), WorldPoint(80.0, 80.0), WorldPoint(-10.0, 80.0))) addPoint(p)
    }

    @Test
    fun aCpuBlendModeAndAnAdjustmentLayerRender() {
        val state = CanvasState()
        state.loadLayers(listOf(Layer("a", "A"), Layer("b", "B", blend = LayerBlend.SUBTRACT)))
        state.loadStrokes(listOf(filled(Color(0xFF999999)).onLayer("a"), filled(Color(0xFF333333)).onLayer("b")))
        val subtracted = render(state).getPixel(32, 32)
        assertEquals(0x66.toDouble(), (subtracted and 0xFF).toDouble(), 2.0) // 0x99 - 0x33
        state.addAdjustmentLayer(Levels(outWhite = 0.5f))
        assertEquals(0x33.toDouble(), (render(state).getPixel(32, 32) and 0xFF).toDouble(), 2.0) // halved
    }

    @Test
    fun blendAndAdjustmentAreSaved() {
        val doc = CanvasStorage.createDocument(context, "blend")
        val layers = listOf(Layer("a", "A", blend = LayerBlend.VIVID_LIGHT), Layer("b", "B", adjustment = Curves(listOf(0f to 0f, 0.4f to 0.6f, 1f to 1f))))
        CanvasStorage.saveLayers(context, doc.id, layers)
        assertEquals(layers, CanvasStorage.loadLayers(context, doc.id))
    }
}
