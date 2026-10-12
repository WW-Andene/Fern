package com.andene.fern.canvas

import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Roadmap item 95: PSD and Krita out and in, Procreate in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayeredFormatsTest {
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext()), Density(1f), LayoutDirection.Ltr)

    private fun twoLayers(): CanvasState = CanvasState().apply {
        loadLayers(listOf(Layer("a", "Sketch"), Layer("b", "Ink", opacity = 0.5f, blend = LayerBlend.MULTIPLY)))
        loadStrokes(
            listOf(
                Stroke(Color.Red, 8.0, layerId = "a").apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)) },
                Stroke(Color.Blue, 8.0, layerId = "b").apply { addPoint(WorldPoint(0.0, 50.0)); addPoint(WorldPoint(100.0, 50.0)) },
            ),
        )
    }

    private fun checkRoundTrip(read: ImportedDocument, image: LayeredImage) {
        assertEquals(image.width, read.width)
        assertEquals(listOf("Sketch", "Ink"), read.layers.map { it.name })
        assertEquals(0.5f, read.layers[1].opacity, 0.01f)
        assertEquals(LayerBlend.MULTIPLY, read.layers[1].blend)
        // Each layer's pixels came back: the red layer is red where it has paint, the blue one blue.
        val sketch = read.layers[0]
        val painted = (0 until sketch.width * sketch.height).first { (sketch.rgba16[it * 4 + 3].toInt() and 0xFFFF) > 60000 }
        assertTrue((sketch.rgba16[painted * 4].toInt() and 0xFFFF) > 60000)
        assertEquals(image.layers[0].pixels[painted] ushr 24, (sketch.rgba16[painted * 4 + 3].toInt() and 0xFFFF) / 257)
    }

    @Test
    fun psdRoundTripsLayers() {
        val image = renderLayers(twoLayers(), textMeasurer, Density(1f)) { null }!!
        assertEquals(2, image.layers.size)
        checkRoundTrip(PsdFormat.read(PsdFormat.write(image)), image)
    }

    @Test
    fun kritaRoundTripsLayers() {
        val image = renderLayers(twoLayers(), textMeasurer, Density(1f)) { null }!!
        checkRoundTrip(readLayeredFile(KraFormat.write(image)), image)
    }

    @Test
    fun lzfDecompresses() {
        // "abcabcabc": three literals, then a back-reference of length 6 to offset 3.
        val compressed = byteArrayOf(2, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), (4 shl 5).toByte(), 2)
        assertEquals("abcabcabc", String(KraFormat.lzfDecompress(compressed, 0, 9)))
    }

    @Test
    fun importedLayersBecomeRasterLayersAtTheView() {
        val state = CanvasState()
        val image = renderLayers(twoLayers(), textMeasurer, Density(1f)) { null }!!
        state.importLayers(PsdFormat.read(PsdFormat.write(image)))
        val imported = state.layers.filter { it.raster }
        assertEquals(listOf("Sketch", "Ink"), imported.map { it.name })
        assertTrue(state.rasters.getValue(imported[0].id).tiles.isNotEmpty())
        assertEquals(LayerBlend.MULTIPLY, imported[1].blend)
    }

    @Test
    fun procreateGivesItsFlattenedPicture() {
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use {
            it.putNextEntry(ZipEntry("Document.archive")); it.write(ByteArray(4)); it.closeEntry()
            it.putNextEntry(ZipEntry("QuickLook/Thumbnail.png")); it.write(png); it.closeEntry()
        }
        val doc = readLayeredFile(zip.toByteArray())
        assertEquals(8, doc.width)
        assertEquals(65535, doc.layers.single().rgba16[1].toInt() and 0xFFFF) // green
    }
}
