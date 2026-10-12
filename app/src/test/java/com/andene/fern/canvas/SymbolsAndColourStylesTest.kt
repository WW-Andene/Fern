package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 100: shared colour styles and the symbol library. */
@RunWith(RobolectricTestRunner::class)
class SymbolsAndColourStylesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val brand = Color(0xFF2E7D32)
    private val newBrand = Color(0xFFC62828)

    private fun line(color: Color) = Stroke(color, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(10.0, 0.0)) }

    @Test
    fun changingASavedColourRecolorsEverythingDrawnInItKeepingOpacity() {
        val state = CanvasState()
        state.addSavedColor(brand)
        state.loadStrokes(listOf(line(brand), line(brand.copy(alpha = 0.5f)), line(Color.Black)))
        state.loadTextItems(listOf(TextItem("t", "Logo", WorldPoint.Zero, 12.0, brand)))
        state.replaceColorEverywhere(brand, newBrand)
        assertEquals(listOf(newBrand, newBrand.copy(alpha = 0.5f), Color.Black), state.strokes.map { it.color })
        assertEquals(newBrand, state.textItems.single().color)
        assertEquals(listOf(newBrand), state.savedColors.toList())
        state.undo()
        assertEquals(brand, state.strokes[0].color)
        assertEquals(brand, state.textItems.single().color)
    }

    @Test
    fun symbolsAreSavedListedLoadedAndDeleted() {
        val star = listOf(line(Color.Blue), line(Color.Red))
        val meta = SymbolLibrary.save(context, "Star", star)
        assertEquals(listOf(meta), SymbolLibrary.list(context))
        val loaded = SymbolLibrary.load(context, meta.id)
        assertEquals(star.map { it.points }, loaded.map { it.points })
        // Placing it pastes an independent copy into a document.
        val state = CanvasState()
        state.pasteStrokes(loaded)
        assertEquals(2, state.strokes.size)
        SymbolLibrary.delete(context, meta.id)
        assertTrue(SymbolLibrary.list(context).isEmpty())
        assertTrue(SymbolLibrary.load(context, meta.id).isEmpty())
    }
}
