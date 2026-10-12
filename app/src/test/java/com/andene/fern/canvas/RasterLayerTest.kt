package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Roadmap item 89: raster layers - tiles, painting, undo, storage at 8 and 16 bits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RasterLayerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun rasterState(depth: Int = 8) = CanvasState().apply {
        addRasterLayer(depth)
        activeColor = Color.Red
    }

    @Test
    fun paintingFillsPixelsOnlyWhereThePenWent() {
        val state = rasterState()
        val surface = state.rasters.getValue(state.activeLayerId)
        state.beginStroke(WorldPoint(10.0, 10.0))
        state.extendStroke(WorldPoint(100.0, 10.0))
        state.endStroke()
        assertTrue(state.strokes.isEmpty()) // pixels, not strokes
        assertEquals(1f, surface.pixel(50, 10)[0], 0.01f) // red along the line
        assertEquals(1f, surface.pixel(50, 10)[3], 0.01f)
        assertEquals(0f, surface.pixel(50, 50)[3], 0f) // nothing far off it
        assertEquals(1, surface.tiles.size) // tiles only where there's paint
    }

    @Test
    fun undoAndRedoRestorePixels() {
        val state = rasterState()
        val surface = state.rasters.getValue(state.activeLayerId)
        state.beginStroke(WorldPoint(10.0, 10.0)); state.endStroke()
        state.beginStroke(WorldPoint(10.0, 300.0)); state.endStroke() // in another tile
        state.undo()
        assertEquals(1f, surface.pixel(10, 10)[3], 0.01f)
        assertEquals(0f, surface.pixel(10, 300)[3], 0f)
        state.undo()
        assertEquals(0f, surface.pixel(10, 10)[3], 0f)
        state.redo()
        state.redo()
        assertEquals(1f, surface.pixel(10, 300)[3], 0.01f)
    }

    @Test
    fun theEraserRemovesPaint() {
        val state = rasterState()
        val surface = state.rasters.getValue(state.activeLayerId)
        state.beginStroke(WorldPoint(10.0, 10.0)); state.endStroke()
        state.beginErase(WorldPoint(10.0, 10.0)); state.endErase()
        assertEquals(0f, surface.pixel(10, 10)[3], 0.02f)
    }

    @Test
    fun tilesAreSavedAtTheLayersDepth() {
        for (depth in listOf(8, 16)) {
            val state = rasterState(depth)
            state.activeOpacity = 0.3 // a translucent value that 8 bits rounds and 16 bits keeps
            state.beginStroke(WorldPoint(5.0, 5.0)); state.endStroke()
            val doc = CanvasStorage.createDocument(context, "raster $depth")
            for ((id, change) in state.takeDirtyRasterTiles()) CanvasStorage.saveRasterTiles(context, doc.id, id, change.first, change.second)
            val loaded = CanvasStorage.loadRasters(context, doc.id, listOf(state.activeLayerId)).getValue(state.activeLayerId)
            assertEquals(depth, loaded.depth)
            val original = state.rasters.getValue(state.activeLayerId).pixel(5, 5)[3]
            val tolerance = if (depth == 16) 1e-4f else 1f / 255
            assertEquals(original, loaded.pixel(5, 5)[3], tolerance)
            // Both depths render a bitmap.
            loaded.bitmapOf(loaded.tiles.values.first())
        }
    }

    @Test
    fun emptyRasterLayersAreKeptByRemoveEmptyOnlyWhenPainted() {
        val state = rasterState()
        val painted = state.activeLayerId
        state.beginStroke(WorldPoint(5.0, 5.0)); state.endStroke()
        state.addRasterLayer(8) // empty
        state.deleteEmptyLayers()
        assertTrue(state.layers.any { it.id == painted })
        assertEquals(1, state.layers.count { it.raster })
    }

    @Test
    fun bundlesCarryRasterPixelsAndTheSoundtrack() {
        val state = rasterState(16)
        state.beginStroke(WorldPoint(5.0, 5.0)); state.endStroke()
        val doc = CanvasStorage.createDocument(context, "bundle")
        CanvasStorage.save(context, doc.id, emptyList())
        CanvasStorage.saveLayers(context, doc.id, state.layers.toList())
        for ((id, change) in state.takeDirtyRasterTiles()) CanvasStorage.saveRasterTiles(context, doc.id, id, change.first, change.second)
        val track = CanvasStorage.importSoundtrack(context, byteArrayOf(1, 2, 3).inputStream(), "ogg", replacing = null)
        CanvasStorage.saveAnimationSettings(context, doc.id, AnimationSettings(soundtrack = track))

        val imported = importDocumentBundle(context, exportDocumentBundle(context, doc.id, "bundle").inputStream())!!
        val layerId = state.activeLayerId
        val pixels = CanvasStorage.loadRasters(context, imported.id, listOf(layerId)).getValue(layerId)
        assertEquals(16, pixels.depth)
        assertEquals(1f, pixels.pixel(5, 5)[3], 1e-3f)
        val importedTrack = CanvasStorage.loadAnimationSettings(context, imported.id).soundtrack!!
        assertTrue(importedTrack != track) // its own copy
        assertEquals(listOf<Byte>(1, 2, 3), CanvasStorage.soundtrackFile(context, importedTrack).readBytes().toList())
    }
}
