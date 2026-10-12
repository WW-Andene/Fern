package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 98: text on a path and outlined text - made, moved, saved and exported. */
@RunWith(RobolectricTestRunner::class)
class TextOnPathTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aSelectedLineBecomesThePathOfANewLabel() {
        val state = CanvasState()
        state.loadStrokes(listOf(Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(10.0, 10.0)); addPoint(WorldPoint(110.0, 60.0)) }))
        state.selectAllOnActiveLayer()
        state.beginTextOnPath()
        state.confirmTextEdit("Along", outlined = true)
        val label = state.textItems.single()
        assertEquals(WorldPoint(10.0, 10.0), label.position)
        assertEquals(listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 50.0)), label.path)
        assertTrue(label.outlined)
        // Its bounds cover the path, so it can be tapped anywhere along it.
        val (min, max) = state.textBounds(label)
        assertTrue(min.x < 10.0 && max.x > 110.0 && max.y > 60.0)
        assertEquals(1, state.strokes.size) // the guide line stays
    }

    @Test
    fun pathAndOutlineAreSavedAndExported() {
        val doc = CanvasStorage.createDocument(context, "Sign")
        val label = TextItem("t", "Hello & co", WorldPoint(5.0, 5.0), 20.0, Color.Black, outlined = true, path = listOf(WorldPoint.Zero, WorldPoint(50.0, 0.0)))
        CanvasStorage.saveTextItems(context, doc.id, listOf(label))
        assertEquals(listOf(label), CanvasStorage.loadTextItems(context, doc.id))
        val state = CanvasState()
        state.loadTextItems(listOf(label))
        state.loadStrokes(listOf(Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(60.0, 40.0)) }))
        val svg = exportDocumentSvg(state) { null }!!
        assertTrue(svg.contains("<textPath href=\"#tp0\">Hello &amp; co</textPath>"))
        assertTrue(svg.contains("fill=\"none\" stroke="))
    }
}
