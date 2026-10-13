package com.andene.fern.canvas

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** Design roadmap D87: new things are named in the user's language, and don't share a name. */
@RunWith(RobolectricTestRunner::class)
class DefaultNamesTest {
    @Test
    fun layersAreNamedInTheUsersLanguage() {
        val base: Context = ApplicationProvider.getApplicationContext()
        val french = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.FRENCH) })
        val state = CanvasState()
        state.names = DefaultNames.fromResources(french)
        state.addLayer()
        assertEquals("Calque 2", state.layers.last().name)
        state.addRasterLayer(8)
        assertEquals("Pixels 3", state.layers.last().name)
    }

    @Test
    fun aNameAlreadyTakenGetsTheNextNumber() {
        assertEquals("Untitled", uniqueName("Untitled", listOf("Sketch")))
        assertEquals("Untitled 2", uniqueName("Untitled", listOf("Untitled")))
        assertEquals("Untitled 3", uniqueName("Untitled", listOf("Untitled", "Untitled 2")))
    }
}
