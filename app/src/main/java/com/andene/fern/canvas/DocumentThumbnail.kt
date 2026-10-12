package com.andene.fern.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayOutputStream

/** Thumbnail edge length in px; 128 is on the project's size scale (CLAUDE.md §7). */
const val THUMBNAIL_SIZE_PX = 128

// Keeps strokes off the bitmap's edge; 8 is on the §7 scale.
private const val THUMBNAIL_PADDING_PX = 8

/**
 * Renders [strokes] fitted into a square thumbnail and returns it PNG-encoded, or null when
 * there's nothing to draw. This is a simplified preview (plain polylines in each stroke's color,
 * width scaled with the fit), not the full pen-type/pressure rendering of `DrawingCanvas`:
 * at 128 px those differences aren't visible, and it keeps thumbnail generation cheap enough
 * to run on every save.
 */
fun renderThumbnailPng(strokes: List<Stroke>): ByteArray? {
    // First animation frame only (all strokes outside animation): overlaid frames would be a smudge.
    val drawn = strokes.filter { it.points.isNotEmpty() && it.animFrame == 0 }
    if (drawn.isEmpty()) return null
    val minX = drawn.minOf { it.minX }
    val minY = drawn.minOf { it.minY }
    val width = (drawn.maxOf { it.maxX } - minX).coerceAtLeast(1.0)
    val height = (drawn.maxOf { it.maxY } - minY).coerceAtLeast(1.0)
    val inner = (THUMBNAIL_SIZE_PX - 2 * THUMBNAIL_PADDING_PX).toDouble()
    val fit = inner / maxOf(width, height)
    // Center the content along its shorter axis.
    val offsetX = THUMBNAIL_PADDING_PX + (inner - width * fit) / 2
    val offsetY = THUMBNAIL_PADDING_PX + (inner - height * fit) / 2

    val bitmap = Bitmap.createBitmap(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in drawn) {
        val path = Path()
        stroke.points.forEachIndexed { index, point ->
            val x = (offsetX + (point.x - minX) * fit).toFloat()
            val y = (offsetY + (point.y - minY) * fit).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = stroke.color.toArgb()
        paint.style = if (stroke.filled) Paint.Style.FILL else Paint.Style.STROKE
        // At least 1 px so thin strokes on a big drawing don't vanish from the preview.
        paint.strokeWidth = (stroke.widthWorld * fit).toFloat().coerceAtLeast(1f)
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
}
