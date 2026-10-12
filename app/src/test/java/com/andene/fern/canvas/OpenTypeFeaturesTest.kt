package com.andene.fern.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 98: OpenType features on labels, saved and exported. */
@RunWith(RobolectricTestRunner::class)
class OpenTypeFeaturesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun featuresBecomeSettingsAreSavedAndExported() {
        assertEquals("liga 0, clig 0, tnum", featureSettings(setOf(OpenTypeFeature.TABULAR_FIGURES, OpenTypeFeature.NO_LIGATURES)))
        val state = CanvasState()
        state.beginTextEdit(WorldPoint(0.0, 0.0))
        state.confirmTextEdit("1/2 office", features = setOf(OpenTypeFeature.FRACTIONS, OpenTypeFeature.NO_LIGATURES))
        val item = state.textItems.single()
        assertEquals(setOf(OpenTypeFeature.FRACTIONS, OpenTypeFeature.NO_LIGATURES), item.features)
        val doc = CanvasStorage.createDocument(context, "otf")
        CanvasStorage.saveTextItems(context, doc.id, listOf(item))
        assertEquals(item.features, CanvasStorage.loadTextItems(context, doc.id).single().features)
        val svg = exportDocumentSvg(state) { null }!!
        assertTrue(svg.contains("font-feature-settings: 'liga' 0, 'clig' 0, 'frac'"))
        state.beginTextEdit(item.position)
        assertEquals(item.features, state.pendingTextEdit!!.initialFeatures)
    }
}
