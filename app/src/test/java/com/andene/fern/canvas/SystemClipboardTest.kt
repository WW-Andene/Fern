package com.andene.fern.canvas

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SystemClipboardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    /**
     * FileProvider caches each authority's roots in a static map, and Robolectric gives every
     * test a fresh data directory: without this, the second test's cache path isn't under the
     * first test's cached root. (On a device one process has one data directory.)
     */
    @Before
    fun clearFileProviderCache() {
        androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    private fun line() = Stroke(Color.Red, 3.0, groupId = "g").apply { addPoint(WorldPoint(1.0, 2.0)); addPoint(WorldPoint(3.0, 4.0)) }

    @Test
    fun fernCopyComesBackAsStrokes() {
        SystemClipboard.copy(context, listOf(line()), png)
        val content = SystemClipboard.read(context)
        assertTrue(content is ClipboardContent.FernStrokes)
        assertEquals(line().points, (content as ClipboardContent.FernStrokes).strokes.single().points)
    }

    @Test
    fun anotherAppsTextIsText() {
        SystemClipboard.copy(context, listOf(line()), png)
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("note", "Buy milk"))
        assertEquals(ClipboardContent.Text("Buy milk"), SystemClipboard.read(context))
    }

    @Test
    fun withoutItsStrokeCacheAFernCopyIsJustTheImage() {
        SystemClipboard.copy(context, listOf(line()), png)
        File(context.cacheDir, "clipboard").deleteRecursively()
        assertTrue(SystemClipboard.read(context) is ClipboardContent.Image)
    }

    @Test
    fun aDraggedSelectionDropsAsStrokesAndLeavesTheClipboardPasteable() {
        SystemClipboard.copy(context, listOf(line()), png)
        val dragged = Stroke(Color.Blue, 2.0).apply { addPoint(WorldPoint(5.0, 6.0)) }
        val drop = SystemClipboard.contentOf(context, SystemClipboard.dragClip(context, listOf(dragged), png))
        assertEquals(dragged.points, (drop as ClipboardContent.FernStrokes).strokes.single().points)
        // The drag kept its files apart: the earlier copy still pastes as strokes.
        assertEquals(line().points, (SystemClipboard.read(context) as ClipboardContent.FernStrokes).strokes.single().points)
    }

    @Test
    fun emptyClipboardIsNothing() {
        context.getSystemService(ClipboardManager::class.java).clearPrimaryClip()
        assertEquals(ClipboardContent.Nothing, SystemClipboard.read(context))
    }

    @Test
    fun pastedStrokesLandCentredOnTheActiveLayerAsANewGroup() {
        val state = CanvasState()
        state.addLayer()
        val stroke = Stroke(Color.Red, 3.0, layerId = "elsewhere", groupId = "g").apply { addPoint(WorldPoint(100.0, 100.0)); addPoint(WorldPoint(110.0, 100.0)) }
        state.pasteStrokes(listOf(stroke))
        val pasted = state.strokes.single()
        assertEquals(state.activeLayerId, pasted.layerId)
        assertEquals(0.0, (pasted.minX + pasted.maxX) / 2, 1e-9) // centred on the view (pan 0, 0)
        assertTrue(pasted.groupId != null && pasted.groupId != "g")
        assertEquals(listOf(pasted), state.selection.toList())
        state.undo()
        assertTrue(state.strokes.isEmpty())
    }

    @Test
    fun pastedTextBecomesALabel() {
        val state = CanvasState()
        state.pasteText("Hello")
        assertEquals("Hello", state.textItems.single().text)
        state.undo()
        assertTrue(state.textItems.isEmpty())
    }

    @Test
    fun droppedClipsAreClassifiedLikePastedOnes() {
        val image = ClipData(ClipDescription("photo", arrayOf("image/jpeg")), ClipData.Item(android.net.Uri.parse("content://other.app/photo/1")))
        assertTrue(SystemClipboard.contentOf(context, image) is ClipboardContent.Image)
        assertEquals(ClipboardContent.Text("hello"), SystemClipboard.contentOf(context, ClipData.newPlainText("t", "hello")))
    }

    @Test
    fun droppedTextAndStrokesLandWhereDropped() {
        val state = CanvasState()
        state.pasteText("Here", at = WorldPoint(500.0, 300.0))
        val (min, max) = state.textBounds(state.textItems.single())
        assertEquals(500.0, (min.x + max.x) / 2, 1e-9)
        assertEquals(300.0, (min.y + max.y) / 2, 1e-9)
        state.pasteStrokes(listOf(line()), at = WorldPoint(-200.0, 40.0))
        val pasted = state.strokes.single()
        assertEquals(-200.0, (pasted.minX + pasted.maxX) / 2, 1e-9)
        assertEquals(40.0, (pasted.minY + pasted.maxY) / 2, 1e-9)
    }
}
