package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextItemsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tapPlacesAPlainLabel() {
        val state = CanvasState()
        state.beginTextGesture(WorldPoint(10.0, 20.0))
        state.continueTextGesture(WorldPoint(12.0, 21.0)) // within the tap slop
        state.endTextGesture()
        val pending = state.pendingTextEdit
        assertNotNull(pending)
        assertNull(pending!!.boxWidthWorld)
        assertEquals(WorldPoint(10.0, 20.0), pending.worldPoint)
    }

    @Test
    fun dragDrawsATextBoxFromItsTopLeft() {
        val state = CanvasState()
        state.beginTextGesture(WorldPoint(200.0, 50.0))
        state.continueTextGesture(WorldPoint(100.0, 80.0))
        assertNotNull(state.textBoxDraft)
        state.endTextGesture()
        assertNull(state.textBoxDraft)
        val pending = state.pendingTextEdit!!
        assertEquals(WorldPoint(100.0, 50.0), pending.worldPoint)
        assertEquals(100.0, pending.boxWidthWorld!!, 0.0)
        state.confirmTextEdit("Hello", font = TextFont.SERIF, alignment = TextAlignment.CENTER)
        val item = state.textItems.single()
        assertEquals(TextFont.SERIF, item.font)
        assertEquals(TextAlignment.CENTER, item.alignment)
        assertEquals(100.0, item.boxWidthWorld!!, 0.0)
    }

    @Test
    fun editingCanDropTheBox() {
        val state = CanvasState()
        state.beginTextGesture(WorldPoint(0.0, 0.0)); state.continueTextGesture(WorldPoint(100.0, 10.0)); state.endTextGesture()
        state.confirmTextEdit("Boxed")
        state.beginTextEdit(WorldPoint(1.0, 1.0)) // tap on the label: edit it
        assertEquals(100.0, state.pendingTextEdit!!.boxWidthWorld!!, 0.0)
        state.confirmTextEdit("Boxed", keepBox = false)
        assertNull(state.textItems.single().boxWidthWorld)
    }

    @Test
    fun cancelledGestureOpensNothing() {
        val state = CanvasState()
        state.beginTextGesture(WorldPoint(0.0, 0.0))
        state.cancelTextGesture()
        state.endTextGesture()
        assertNull(state.pendingTextEdit)
    }

    @Test
    fun boundsCountNewlinesAndWrapping() {
        val state = CanvasState()
        val plain = TextItem("a", "ab\nabcd", WorldPoint.Zero, 10.0, Color.Black)
        val (_, plainMax) = state.textBounds(plain)
        assertEquals(4 * 5.5, plainMax.x, 1e-9) // longest line
        assertEquals(2 * 13.0, plainMax.y, 1e-9)
        val boxed = plain.copy(text = "a".repeat(20), boxWidthWorld = 55.0) // 110 wide -> 2 lines
        val (_, boxedMax) = state.textBounds(boxed)
        assertEquals(55.0, boxedMax.x, 1e-9)
        assertEquals(2 * 13.0, boxedMax.y, 1e-9)
    }

    @Test
    fun stylingPersists() {
        val doc = CanvasStorage.createDocument(context, "Text")
        val item = TextItem("t", "Hi", WorldPoint(1.0, 2.0), 12.0, Color.Red, font = TextFont.MONO, alignment = TextAlignment.END, boxWidthWorld = 80.0)
        val plain = TextItem("p", "Plain", WorldPoint(3.0, 4.0), 12.0, Color.Red)
        CanvasStorage.saveTextItems(context, doc.id, listOf(item, plain))
        assertEquals(listOf(item, plain), CanvasStorage.loadTextItems(context, doc.id))
    }

    @Test
    fun svgCarriesFontLinesAndAnchor() {
        val state = CanvasState()
        state.loadTextItems(listOf(TextItem("t", "one\ntwo", WorldPoint(0.0, 0.0), 10.0, Color.Black, font = TextFont.SERIF, alignment = TextAlignment.CENTER, boxWidthWorld = 100.0)))
        val svg = exportDocumentSvg(state) { null }!!
        assertTrue(svg.contains("""font-family="serif""""))
        assertTrue(svg.contains("""text-anchor="middle""""))
        assertTrue(svg.contains(""">one</tspan>""") && svg.contains(""">two</tspan>"""))
        assertTrue(svg.contains("""<text x="50""""))
    }
}
