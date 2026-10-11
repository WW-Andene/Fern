package com.andene.fern.canvas

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.ByteArrayOutputStream

/** Longest edge of an exported image in px; 4096 keeps detail for print-size use and stays well inside bitmap memory limits. */
const val EXPORT_MAX_EDGE_PX = 4096

// Margin around the content, as a fraction of its size, so strokes don't touch the image edge.
private const val EXPORT_MARGIN_RATIO = 0.05

/**
 * Renders everything in [source] (strokes, text, images, paper) to a PNG framed tightly around
 * the content, using the same [drawScene] as the live canvas so the export matches what's on
 * screen. Returns null for an empty document.
 *
 * Draws through a throwaway [CanvasState] with its own camera, holding deep copies of the
 * strokes: the export camera may trigger a floating-origin rebase, which shifts strokes in
 * place and must never touch the live document's.
 */
fun exportDocumentPng(
    source: CanvasState,
    textMeasurer: TextMeasurer,
    density: Density,
    imageFor: (String) -> ImageBitmap?,
): ByteArray? {
    val bounds = exportBounds(source) ?: return null
    val (min, max) = bounds
    val contentWidth = (max.x - min.x).coerceAtLeast(1.0)
    val contentHeight = (max.y - min.y).coerceAtLeast(1.0)
    val marginX = contentWidth * EXPORT_MARGIN_RATIO
    val marginY = contentHeight * EXPORT_MARGIN_RATIO
    val worldWidth = contentWidth + 2 * marginX
    val worldHeight = contentHeight + 2 * marginY
    val scale = EXPORT_MAX_EDGE_PX / maxOf(worldWidth, worldHeight)
    val widthPx = (worldWidth * scale).toInt().coerceAtLeast(1)
    val heightPx = (worldHeight * scale).toInt().coerceAtLeast(1)

    val camera = CanvasState()
    camera.loadStrokes(source.strokes.map { it.deepCopy() })
    camera.loadTextItems(source.textItems.toList())
    camera.loadImageItems(source.imageItems.toList())
    camera.loadBackground(source.backgroundColor, source.backgroundStyle)
    camera.jumpTo(WorldPoint((min.x + max.x) / 2.0, (min.y + max.y) / 2.0), scale)

    val image = ImageBitmap(widthPx, heightPx)
    val size = Size(widthPx.toFloat(), heightPx.toFloat())
    val center = Offset(size.width / 2f, size.height / 2f)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
        drawRect(color = camera.backgroundColor, size = size)
        drawScene(
            camera,
            center,
            camera.screenToWorld(Offset.Zero, center),
            camera.screenToWorld(Offset(size.width, size.height), center),
            textMeasurer,
            imageFor,
        )
    }
    val out = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

/**
 * World-space box around all content. Text is counted by its anchor plus a rough extent
 * (font size per character), since its exact laid-out size isn't known without measuring.
 */
private fun exportBounds(state: CanvasState): Pair<WorldPoint, WorldPoint>? {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fun include(x: Double, y: Double) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    for (stroke in state.strokes) {
        if (stroke.points.isEmpty()) continue
        include(stroke.minX - stroke.widthWorld, stroke.minY - stroke.widthWorld)
        include(stroke.maxX + stroke.widthWorld, stroke.maxY + stroke.widthWorld)
    }
    for (item in state.imageItems) {
        include(item.position.x, item.position.y)
        include(item.position.x + item.widthWorld, item.position.y + item.heightWorld)
    }
    for (item in state.textItems) {
        include(item.position.x, item.position.y)
        include(item.position.x + item.fontSizeWorld * item.text.length, item.position.y + item.fontSizeWorld * 1.5)
    }
    if (minX > maxX) return null
    return WorldPoint(minX, minY) to WorldPoint(maxX, maxY)
}
