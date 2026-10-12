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
        val file = File(context.filesDir, "canvas_${doc.id}.json")
        file.writeText("{ not json")
        assertTrue(CanvasStorage.load(context, doc.id).isEmpty())
        assertTrue(context.filesDir.listFiles()!!.any { it.name.startsWith("canvas_${doc.id}.json.corrupt-") })
    }

    @Test
    fun newerFormatIsNotOverwrittenByOlderBuild() {
        val doc = CanvasStorage.createDocument(context, "Future")
        val file = File(context.filesDir, "canvas_${doc.id}.json")
        file.writeText("""{"formatVersion": 999, "data": []}""")
        assertTrue(CanvasStorage.load(context, doc.id).isEmpty())
        assertTrue(context.filesDir.listFiles()!!.any { it.name.startsWith("canvas_${doc.id}.json.corrupt-") })
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
