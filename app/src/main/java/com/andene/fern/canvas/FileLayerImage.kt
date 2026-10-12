package com.andene.fern.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** An image file's pixels for a file layer (roadmap item 138): non-premultiplied ARGB, [width] x [height]. */
class FileLayerImage(val argb: IntArray, val width: Int, val height: Int)

/** Longest edge a file layer is read at; a larger picture is read at a half, quarter... of its size. */
private const val FILE_LAYER_MAX_EDGE_PX = 4096

/**
 * Reads the image at [uri] for a file layer, scaled down by powers of two to at most
 * [FILE_LAYER_MAX_EDGE_PX] on its long edge; null when the file can't be read (moved, deleted,
 * its permission withdrawn, or not an image).
 */
fun readFileLayerImage(context: Context, uri: Uri): FileLayerImage? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > FILE_LAYER_MAX_EDGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPremultiplied = false
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    FileLayerImage(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
}.getOrNull()
