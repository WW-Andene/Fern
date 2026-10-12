package com.andene.fern.canvas

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.UUID

/** A video copied into the app for embedding (roadmap item 107), with its first-frame picture and that picture's size. */
class EmbeddedVideo(val videoFileName: String, val posterFileName: String, val widthPx: Int, val heightPx: Int)

/**
 * Copies the video at [uri] into [CanvasStorage.videosDir] and saves its first frame (no larger
 * than [maxEdgePx] on its long edge) as a PNG in [CanvasStorage.imagesDir], for an image item
 * that plays it. Null when the file can't be read or has no picture (audio only, or a format
 * the platform can't decode); nothing is left behind then. Blocking: call off the main thread.
 */
fun embedVideo(context: Context, uri: Uri, maxEdgePx: Int): EmbeddedVideo? {
    val extension = when (context.contentResolver.getType(uri)) {
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        "video/x-matroska" -> "mkv"
        else -> "mp4" // MP4 and anything else: the platform reads by content, not by name
    }
    val videoName = "${UUID.randomUUID()}.$extension"
    val target = CanvasStorage.videoFile(context, videoName)
    val copied = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
    }.getOrDefault(false)
    if (!copied) {
        target.delete()
        return null
    }
    val frame = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(target.absolutePath)
                getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                release()
            }
        }
    }.getOrNull()
    if (frame == null) {
        target.delete()
        return null
    }
    val scale = minOf(1f, maxEdgePx.toFloat() / maxOf(frame.width, frame.height))
    val poster = if (scale < 1f) Bitmap.createScaledBitmap(frame, maxOf(1, (frame.width * scale).toInt()), maxOf(1, (frame.height * scale).toInt()), true) else frame
    val posterName = "${UUID.randomUUID()}.png"
    CanvasStorage.imageFile(context, posterName).outputStream().use { poster.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return EmbeddedVideo(videoName, posterName, poster.width, poster.height)
}
