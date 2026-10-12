package com.andene.fern.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 123: layer comps record how the layers are shown and switch between versions. */
@RunWith(RobolectricTestRunner::class)
class LayerCompTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aCompRestoresVisibilityOpacityAndBlend() {
        val state = CanvasState()
        state.addLayer()
        val ink = state.layers[1].id
        val withColour = state.saveLayerComp("Colour")
        state.updateLayer(DEFAULT_LAYER_ID) { it.copy(visible = false) }
        state.updateLayer(ink) { it.copy(opacity = 0.5f, blend = LayerBlend.MULTIPLY) }
        val lineArt = state.saveLayerComp("Line art")
        assertEquals(lineArt, state.currentLayerCompId)

        state.applyLayerComp(withColour)
        assertEquals(withColour, state.currentLayerCompId)
        assertEquals(true, state.layers[0].visible)
        assertEquals(1f, state.layers[1].opacity)

        state.applyLayerComp(lineArt)
        assertFalse(state.layers[0].visible)
        assertEquals(LayerBlend.MULTIPLY, state.layers[1].blend)

        // A layer added later isn't in the comps: applying one leaves it alone.
        state.addLayer()
        state.updateLayer(state.layers[2].id) { it.copy(visible = false) }
        state.applyLayerComp(withColour)
        assertFalse(state.layers[2].visible)

        state.deleteLayerComp(lineArt)
        state.renameLayerComp(withColour, "Final")
        assertEquals(listOf("Final"), state.layerComps.map { it.name })
        state.updateLayer(DEFAULT_LAYER_ID) { it.copy(visible = false) }
        assertNull(state.currentLayerCompId)
    }

    @Test
    fun compsAreSavedWithTheDocumentAndItsCopies() {
        val doc = CanvasStorage.createDocument(context, "Comps")
        val comp = LayerComp("c1", "Night", mapOf("base" to LayerCompState(false, 0.25f, LayerBlend.SCREEN), "ink" to LayerCompState(true, 1f, LayerBlend.NORMAL)))
        CanvasStorage.saveLayerComps(context, doc.id, listOf(comp))
        assertEquals(listOf(comp), CanvasStorage.loadLayerComps(context, doc.id))
        val copy = CanvasStorage.duplicateDocument(context, doc.id, "Comps copy")
        assertEquals(listOf(comp), CanvasStorage.loadLayerComps(context, copy.id))
        CanvasStorage.deleteDocument(context, doc.id)
        assertEquals(emptyList<LayerComp>(), CanvasStorage.loadLayerComps(context, doc.id))
    }
}
