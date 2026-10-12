package com.andene.fern.canvas

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/** What the system clipboard holds, as far as pasting onto the canvas is concerned. */
sealed interface ClipboardContent {
    /** Strokes copied from Fern (this or another document, before or after a restart). */
    data class FernStrokes(val strokes: List<Stroke>) : ClipboardContent
    data class Text(val text: String) : ClipboardContent
    data class Image(val uri: Uri) : ClipboardContent
    data object Nothing : ClipboardContent
}

/**
 * Copy and paste through Android's clipboard, so a selection can go to other apps and come back
 * after a restart, not just between documents in this process.
 *
 * A copy is an image clip: other apps get a PNG of the selected area through the share
 * FileProvider. Fern also keeps the strokes themselves ([StrokeCodec]) in a private cache file
 * named by a token stored in the clip's extras; on paste, a clip carrying a token whose file
 * still exists comes back as editable strokes, while anything else pasted is treated as the
 * text or image it is. A newer copy - by Fern or any other app - replaces the clip, and with it
 * the token, so stale strokes are never pasted.
 */
object SystemClipboard {
    private const val CLIP_LABEL = "Fern selection"
    private const val EXTRA_TOKEN = "com.andene.fern.clip_token"
    // Under cache/shared/, the folder the share FileProvider exposes (see res/xml/share_paths.xml).
    private const val SHARED_DIR = "shared"
    private const val STROKES_DIR = "clipboard"
    private const val AUTHORITY_SUFFIX = ".share"

    /** Puts [strokes] (and [png], the image other apps get) on the clipboard. */
    fun copy(context: Context, strokes: List<Stroke>, png: ByteArray) {
        val token = UUID.randomUUID().toString()
        val strokesDir = File(context.cacheDir, STROKES_DIR).apply { mkdirs() }
        // Only the newest copy matters; earlier ones can't be pasted any more.
        strokesDir.listFiles()?.forEach { it.delete() }
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), strokes) }.toByteArray()
        File(strokesDir, "$token.bin").writeBytes(bytes)

        val imageFile = File(File(context.cacheDir, SHARED_DIR).apply { mkdirs() }, "clipboard.png")
        imageFile.writeBytes(png)
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, imageFile)
        // The MIME type is stated rather than looked up (ClipData.newUri asks the resolver), so
        // receiving apps and read() always see an image clip.
        val description = ClipDescription(CLIP_LABEL, arrayOf("image/png"))
        description.extras = PersistableBundle().apply { putString(EXTRA_TOKEN, token) }
        val clip = ClipData(description, ClipData.Item(uri))
        clipboard(context).setPrimaryClip(clip)
    }

    fun read(context: Context): ClipboardContent {
        val clip = clipboard(context).primaryClip ?: return ClipboardContent.Nothing
        if (clip.itemCount == 0) return ClipboardContent.Nothing
        val token = clip.description.extras?.getString(EXTRA_TOKEN)
        if (token != null && token.matches(Regex("[0-9a-f-]{36}"))) {
            val file = File(File(context.cacheDir, STROKES_DIR), "$token.bin")
            if (file.exists()) {
                val strokes = runCatching { StrokeCodec.read(DataInputStream(ByteArrayInputStream(file.readBytes()))) }.getOrNull()
                if (!strokes.isNullOrEmpty()) return ClipboardContent.FernStrokes(strokes)
            }
        }
        val item = clip.getItemAt(0)
        val uri = item.uri
        if (uri != null && (0 until clip.description.mimeTypeCount).any { ClipDescription.compareMimeTypes(clip.description.getMimeType(it), "image/*") }) {
            return ClipboardContent.Image(uri)
        }
        val text = item.coerceToText(context)?.toString()
        return if (text.isNullOrBlank()) ClipboardContent.Nothing else ClipboardContent.Text(text)
    }

    private fun clipboard(context: Context) = context.getSystemService(ClipboardManager::class.java)
}
