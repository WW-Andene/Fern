package com.andene.fern.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 138: file layers show an image file and reload from it. */
@RunWith(RobolectricTestRunner::class)
class FileLayerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aFileLayerShowsTheImageLockedAndReloads() {
        val state = CanvasState()
        val red = IntArray(4 * 2) { 0xFFFF0000.toInt() }
        state.addFileLayer("photo", "content://example/photo.png", red, 4, 2)
        val layer = state.layers.last()
        assertTrue(layer.raster && layer.locked)
        assertEquals("content://example/photo.png", layer.fileSource)
        val surface = state.rasters.getValue(layer.id)
        assertEquals(1f, surface.pixel(3, 1)[0], 1e-3f)
        assertEquals(0f, surface.pixel(4, 1)[3], 1e-6f) // nothing past the image

        // The file changed: half the size, blue.
        state.reloadFileLayer(layer.id, IntArray(2) { 0xFF0000FF.toInt() }, 2, 1)
        assertEquals(1f, surface.pixel(0, 0)[2], 1e-3f)
        assertEquals(0f, surface.pixel(3, 1)[3], 1e-6f) // the old pixels are gone
        state.undo()
        assertEquals(1f, surface.pixel(3, 1)[0], 1e-3f)
    }

    @Test
    fun theFileIsSavedWithTheLayer() {
        val doc = CanvasStorage.createDocument(context, "File layers")
        CanvasStorage.saveLayers(context, doc.id, listOf(Layer("f", "Photo", locked = true, raster = true, fileSource = "content://x/y.jpg")))
        assertEquals("content://x/y.jpg", CanvasStorage.loadLayers(context, doc.id).single().fileSource)
    }
}
