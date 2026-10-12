package com.andene.fern.canvas

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File

/** Roadmap item 107: embedded videos are saved, bundled, and an unreadable one leaves nothing behind. */
@RunWith(RobolectricTestRunner::class)
class VideoEmbedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theVideoFileNameIsSavedWithTheImageItem() {
        val items = listOf(
            ImageItem("a", "poster.png", WorldPoint(1.0, 2.0), 30.0, 20.0, videoFileName = "clip.mp4"),
            ImageItem("b", "photo.png", WorldPoint(0.0, 0.0), 10.0, 10.0),
        )
        CanvasStorage.saveImageItems(context, "doc", items)
        assertEquals(items, CanvasStorage.loadImageItems(context, "doc"))
    }

    @Test
    fun bundlesCarryTheVideoFile() {
        val meta = CanvasStorage.createDocument(context, "Board")
        CanvasStorage.videoFile(context, "0a1b-2c.mp4").writeBytes(byteArrayOf(1, 2, 3))
        CanvasStorage.imageFile(context, "0a1b-poster.png").writeBytes(byteArrayOf(9))
        CanvasStorage.saveImageItems(context, meta.id, listOf(ImageItem("v", "0a1b-poster.png", WorldPoint.Zero, 10.0, 10.0, "0a1b-2c.mp4")))
        val bundle = exportDocumentBundle(context, meta.id, "Board")
        CanvasStorage.videoFile(context, "0a1b-2c.mp4").delete()
        val imported = importDocumentBundle(context, ByteArrayInputStream(bundle))!!
        assertEquals("0a1b-2c.mp4", CanvasStorage.loadImageItems(context, imported.id).single().videoFileName)
        assertTrue(CanvasStorage.videoFile(context, "0a1b-2c.mp4").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun anUnreadableVideoLeavesNoFileBehind() {
        val source = File(context.cacheDir, "not-a-video.mp4").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val before = CanvasStorage.videosDir(context).list()?.toSet().orEmpty()
        assertNull(embedVideo(context, Uri.fromFile(source), 2048))
        assertEquals(before, CanvasStorage.videosDir(context).list()?.toSet().orEmpty())
    }
}
