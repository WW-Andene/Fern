package com.andene.fern.canvas

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
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

/** Longest edge of an exported PDF page in points: 842 pt is A4's long side. */
const val PDF_MAX_EDGE_PT = 842

// Margin around the content, as a fraction of its size, so strokes don't touch the image edge.
private const val EXPORT_MARGIN_RATIO = 0.05

/**
 * A document framed for export: a throwaway [CanvasState] whose camera fits all content into
 * [widthPx] x [heightPx]. It holds deep copies of the strokes, because the export camera may
 * trigger a floating-origin rebase, which shifts strokes in place and must never touch the
 * live document's.
 */
class ExportFrame(val camera: CanvasState, val widthPx: Int, val heightPx: Int) {
    /** Draws the framed document with the same [drawScene] as the live canvas, so exports match the screen. */
    /** With [transparent], the paper colour and pattern are left out, leaving only the content on a clear background. */
    fun drawOn(canvas: Canvas, density: Density, textMeasurer: TextMeasurer, imageFor: (String) -> ImageBitmap?, transparent: Boolean = false) {
        val size = Size(widthPx.toFloat(), heightPx.toFloat())
        val center = Offset(size.width / 2f, size.height / 2f)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
            if (!transparent) drawRect(color = camera.backgroundColor, size = size)
            drawScene(
                camera,
                center,
                camera.screenToWorld(Offset.Zero, center),
                camera.screenToWorld(Offset(size.width, size.height), center),
                textMeasurer,
                imageFor,
                drawPaper = !transparent,
            )
        }
    }
}

/**
 * Frames [source] so the longest edge is [maxEdgePx]: exactly [region] when given (a canvas
 * [Frame]), otherwise all content plus a 5% margin. Null for an empty document.
 */
fun frameForExport(source: CanvasState, maxEdgePx: Int, region: Frame? = null): ExportFrame? {
    val (min, max) = region?.let { it.min to it.max } ?: exportBounds(source) ?: return null
    val margin = if (region == null) 1 + 2 * EXPORT_MARGIN_RATIO else 1.0
    val worldWidth = (max.x - min.x).coerceAtLeast(1e-9) * margin
    val worldHeight = (max.y - min.y).coerceAtLeast(1e-9) * margin
    val scale = maxEdgePx / maxOf(worldWidth, worldHeight)

    val camera = CanvasState()
    camera.loadStrokes(source.strokes.map { it.deepCopy() })
    camera.loadLayers(source.layers.toList())
    camera.loadFrames(source.frames.toList())
    camera.loadTextItems(source.textItems.toList())
    camera.loadImageItems(source.imageItems.toList())
    camera.loadBackground(source.backgroundColor, source.backgroundStyle)
    camera.jumpTo(WorldPoint((min.x + max.x) / 2.0, (min.y + max.y) / 2.0), scale)
    return ExportFrame(
        camera,
        (worldWidth * scale).toInt().coerceAtLeast(1),
        (worldHeight * scale).toInt().coerceAtLeast(1),
    )
}

/** The whole document as a PNG at [EXPORT_MAX_EDGE_PX], or null if it's empty. */
fun exportDocumentPng(
    source: CanvasState,
    textMeasurer: TextMeasurer,
    density: Density,
    imageFor: (String) -> ImageBitmap?,
    region: Frame? = null,
    transparent: Boolean = false,
): ByteArray? {
    val frame = frameForExport(source, EXPORT_MAX_EDGE_PX, region) ?: return null
    val image = ImageBitmap(frame.widthPx, frame.heightPx)
    frame.drawOn(Canvas(image), density, textMeasurer, imageFor, transparent)
    val out = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

// Each frame's cell in a sprite sheet; 512 px keeps a 24-frame sheet (5x5 grid) at 2560 px.
private const val SPRITE_FRAME_EDGE_PX = 512

/**
 * Every animation frame as a sprite sheet: a near-square grid, left to right then top to
 * bottom, each cell framed identically (all frames' content together), so a game engine or web
 * page can step through equal cells. Null for an empty document.
 */
fun exportSpriteSheetPng(
    source: CanvasState,
    textMeasurer: TextMeasurer,
    density: Density,
    imageFor: (String) -> ImageBitmap?,
): ByteArray? {
    val frame = frameForExport(source, SPRITE_FRAME_EDGE_PX) ?: return null
    val count = source.animFrameCount
    val columns = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()
    val rows = (count + columns - 1) / columns
    val sheet = ImageBitmap(frame.widthPx * columns, frame.heightPx * rows)
    val canvas = Canvas(sheet)
    for (i in 0 until count) {
        frame.camera.goToAnimFrame(i)
        canvas.save()
        canvas.translate((i % columns * frame.widthPx).toFloat(), (i / columns * frame.heightPx).toFloat())
        frame.drawOn(canvas, density, textMeasurer, imageFor)
        canvas.restore()
    }
    val out = ByteArrayOutputStream()
    sheet.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

/** Every animation frame as a looping animated GIF at the document's playback speed; null for an empty document. */
fun exportAnimatedGif(
    source: CanvasState,
    textMeasurer: TextMeasurer,
    density: Density,
    imageFor: (String) -> ImageBitmap?,
): ByteArray? {
    val frame = frameForExport(source, SPRITE_FRAME_EDGE_PX) ?: return null
    val pixels = (0 until source.animFrameCount).map { i ->
        frame.camera.goToAnimFrame(i)
        val image = ImageBitmap(frame.widthPx, frame.heightPx)
        frame.drawOn(Canvas(image), density, textMeasurer, imageFor)
        IntArray(frame.widthPx * frame.heightPx).also { image.readPixels(it) }
    }
    // GIF delays are in hundredths of a second.
    return encodeGif(pixels, frame.widthPx, frame.heightPx, delayCentis = (100 / source.animFps).coerceAtLeast(2))
}

/**
 * The whole document as a PDF, or null if it's empty: one page per canvas [Frame] when the
 * document has frames (a multi-page document laid out on the canvas), otherwise one page
 * holding all content. Drawing goes through the
 * same draw calls as the screen, which PdfDocument records as vector operations, so strokes
 * and text stay sharp at any zoom in a PDF viewer. The page's longest edge is
 * [PDF_MAX_EDGE_PT] points (A4's long side).
 */
fun exportDocumentPdf(
    source: CanvasState,
    textMeasurer: TextMeasurer,
    imageFor: (String) -> ImageBitmap?,
): ByteArray? {
    val regions: List<Frame?> = source.frames.toList().ifEmpty { listOf(null) }
    val pages = regions.mapNotNull { frameForExport(source, PDF_MAX_EDGE_PT, it) }
    if (pages.isEmpty()) return null
    val pdf = PdfDocument()
    pages.forEachIndexed { index, frame ->
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(frame.widthPx, frame.heightPx, index + 1).create())
        // PDF units are points; density 1 so text sizes map one-to-one onto the page.
        frame.drawOn(Canvas(page.canvas), Density(1f), textMeasurer, imageFor)
        pdf.finishPage(page)
    }
    val out = ByteArrayOutputStream()
    pdf.writeTo(out)
    pdf.close()
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
