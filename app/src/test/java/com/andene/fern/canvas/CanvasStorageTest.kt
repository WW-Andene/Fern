package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CanvasStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun strokesRoundTrip() {
        val doc = CanvasStorage.createDocument(context, "Test")
        val stroke = Stroke(Color.Red, 3.0, PenType.PENCIL, dashed = true, layerId = "ink", animFrame = 2).apply {
            addPoint(WorldPoint(1.5, -2.25), pressure = 0.5f, tilt = 0.25f, orientation = 1f)
            addPoint(WorldPoint(1e6, 3.0))
        }
        CanvasStorage.save(context, doc.id, listOf(stroke), withThumbnail = false)

        val loaded = CanvasStorage.load(context, doc.id).single()
        assertEquals(stroke.points, loaded.points)
        assertEquals(stroke.pressures, loaded.pressures)
        assertEquals(stroke.tilts, loaded.tilts)
        assertEquals(stroke.penType, loaded.penType)
        assertEquals(stroke.dashed, loaded.dashed)
        assertEquals(stroke.layerId, loaded.layerId)
        assertEquals(stroke.animFrame, loaded.animFrame)
        assertEquals(stroke.widthWorld, loaded.widthWorld, 0.0)
    }

    @Test
    fun corruptFileIsQuarantinedNotFatal() {
        val doc = CanvasStorage.createDocument(context, "Broken")
        val file = File(context.filesDir, "strokes_${doc.id}.bin")
        file.writeText("{ not a stroke file")
        assertTrue(CanvasStorage.load(context, doc.id).isEmpty())
        assertTrue(context.filesDir.listFiles()!!.any { it.name.startsWith("strokes_${doc.id}.bin.corrupt-") })
    }

    @Test
    fun legacyJsonDocumentIsReadThenReplacedByBinary() {
        val doc = CanvasStorage.createDocument(context, "Old")
        File(context.filesDir, "strokes_${doc.id}.bin").delete()
        val legacy = File(context.filesDir, "canvas_${doc.id}.json")
        legacy.writeText("""{"formatVersion":1,"data":[{"color":-16777216,"width":2.0,"points":[{"x":1.0,"y":2.0},{"x":3.0,"y":4.0,"pressure":0.5}]}]}""")
        val loaded = CanvasStorage.load(context, doc.id).single()
        assertEquals(listOf(WorldPoint(1.0, 2.0), WorldPoint(3.0, 4.0)), loaded.points)
        assertEquals(listOf(1f, 0.5f), loaded.pressures)
        CanvasStorage.save(context, doc.id, listOf(loaded), withThumbnail = false)
        assertTrue(!legacy.exists())
        assertEquals(loaded.points, CanvasStorage.load(context, doc.id).single().points)
    }

    private fun line(x: Double) = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(x, 0.0)); addPoint(WorldPoint(x, 5.0)) }

    @Test
    fun appendedStrokesGoToTheJournalAndLoadBack() {
        val doc = CanvasStorage.createDocument(context, "Journal")
        val base = File(context.filesDir, "strokes_${doc.id}.bin")
        val journal = File(context.filesDir, "strokes_${doc.id}.journal")
        val strokes = mutableListOf(line(0.0))
        CanvasStorage.save(context, doc.id, strokes.toList(), withThumbnail = false)
        val baseSize = base.length()
        strokes += line(1.0)
        CanvasStorage.save(context, doc.id, strokes.toList(), withThumbnail = false)
        strokes += line(2.0)
        CanvasStorage.save(context, doc.id, strokes.toList(), withThumbnail = false)
        assertEquals(baseSize, base.length()) // not rewritten
        assertTrue(journal.exists())
        assertEquals(listOf(0.0, 1.0, 2.0), CanvasStorage.load(context, doc.id).map { it.points.first().x })
    }

    @Test
    fun editingASavedStrokeRewritesInFull() {
        val doc = CanvasStorage.createDocument(context, "Edit")
        val journal = File(context.filesDir, "strokes_${doc.id}.journal")
        val first = line(0.0)
        CanvasStorage.save(context, doc.id, listOf(first), withThumbnail = false)
        CanvasStorage.save(context, doc.id, listOf(first, line(1.0)), withThumbnail = false)
        assertTrue(journal.exists())
        // In-place mutation, as a floating-origin rebase does: same object, new revision.
        first.shiftBy(WorldPoint(10.0, 0.0))
        CanvasStorage.save(context, doc.id, listOf(first, line(1.0)), withThumbnail = false)
        assertTrue(!journal.exists())
        assertEquals(listOf(-10.0, 1.0), CanvasStorage.load(context, doc.id).map { it.points.first().x })
    }

    @Test
    fun damagedJournalTailKeepsEarlierBlocks() {
        val doc = CanvasStorage.createDocument(context, "Crash")
        val journal = File(context.filesDir, "strokes_${doc.id}.journal")
        val strokes = listOf(line(0.0), line(1.0))
        CanvasStorage.save(context, doc.id, strokes.take(1), withThumbnail = false)
        CanvasStorage.save(context, doc.id, strokes, withThumbnail = false)
        journal.appendBytes(byteArrayOf(0x46, 0x53, 0x54)) // a block cut off mid-write
        assertEquals(listOf(0.0, 1.0), CanvasStorage.load(context, doc.id).map { it.points.first().x })
        assertTrue(!journal.exists()) // rewritten cleanly
        assertEquals(2, CanvasStorage.load(context, doc.id).size)
    }

    @Test
    fun bundleIncludesJournaledStrokes() {
        val doc = CanvasStorage.createDocument(context, "Journal bundle")
        CanvasStorage.save(context, doc.id, listOf(line(0.0)), withThumbnail = false)
        val loaded = CanvasStorage.load(context, doc.id)
        CanvasStorage.save(context, doc.id, loaded + line(1.0), withThumbnail = false)
        val imported = importDocumentBundle(context, exportDocumentBundle(context, doc.id, "B").inputStream())!!
        assertEquals(2, CanvasStorage.load(context, imported.id).size)
    }

    @Test
    fun historyRoundTrips() {
        val doc = CanvasStorage.createDocument(context, "History")
        val a = listOf(Stroke(Color.Red, 1.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(1.0, 1.0)) })
        CanvasStorage.saveHistory(context, doc.id, undo = listOf(emptyList(), a), redo = listOf(a))
        val (undo, redo) = CanvasStorage.loadHistory(context, doc.id)
        assertEquals(listOf(0, 1), undo.map { it.size })
        assertEquals(a.single().points, redo.single().single().points)
    }

    @Test
    fun bundleRoundTripsStrokes() {
        val doc = CanvasStorage.createDocument(context, "Bundle")
        val stroke = Stroke(Color.Green, 2.0).apply { addPoint(WorldPoint(7.0, 8.0)); addPoint(WorldPoint(9.0, 10.0)) }
        CanvasStorage.save(context, doc.id, listOf(stroke), withThumbnail = false)
        val imported = importDocumentBundle(context, exportDocumentBundle(context, doc.id, "Bundle").inputStream())!!
        assertEquals(stroke.points, CanvasStorage.load(context, imported.id).single().points)
    }

    @Test
    fun newerFormatIsNotOverwrittenByOlderBuild() {
        val doc = CanvasStorage.createDocument(context, "Future")
        val file = File(context.filesDir, "strokes_${doc.id}.bin")
        // Magic "FSTK", then version 999.
        file.writeBytes(byteArrayOf(0x46, 0x53, 0x54, 0x4B, 0, 0, 0x03, 0xE7.toByte()))
        assertTrue(CanvasStorage.load(context, doc.id).isEmpty())
        assertTrue(context.filesDir.listFiles()!!.any { it.name.startsWith("strokes_${doc.id}.bin.corrupt-") })
    }

    @Test
    fun documentLifecycle() {
        val doc = CanvasStorage.createDocument(context, "Life")
        CanvasStorage.renameDocument(context, doc.id, "Renamed")
        assertEquals("Renamed", CanvasStorage.listDocuments(context).single { it.id == doc.id }.name)
        CanvasStorage.moveToTrash(context, doc.id)
        assertTrue(CanvasStorage.listDocuments(context).single { it.id == doc.id }.trashedAt != null)
        CanvasStorage.restoreFromTrash(context, doc.id)
        assertEquals(null, CanvasStorage.listDocuments(context).single { it.id == doc.id }.trashedAt)
    }
}
