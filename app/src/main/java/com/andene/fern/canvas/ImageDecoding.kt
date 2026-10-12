package com.andene.fern.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Decodes the image at [uri] no larger than [maxEdgePx] on its longest edge: first reads only
 * the size, decodes with the largest power-of-two subsampling that stays at or above the
 * target (cheap, done by the decoder), then scales the rest of the way exactly.
 */
fun decodeDownsampled(context: Context, uri: Uri, maxEdgePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / (sample * 2) >= maxEdgePx) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val decodedLongest = maxOf(decoded.width, decoded.height)
    if (decodedLongest <= maxEdgePx) return decoded
    val factor = maxEdgePx.toFloat() / decodedLongest
    return Bitmap.createScaledBitmap(decoded, (decoded.width * factor).toInt(), (decoded.height * factor).toInt(), true)
        .also { if (it !== decoded) decoded.recycle() }
}
