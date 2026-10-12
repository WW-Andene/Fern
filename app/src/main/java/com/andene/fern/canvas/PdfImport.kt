package com.andene.fern.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.util.UUID

/** A PDF page rendered to an image file under [CanvasStorage.imagesDir], with its pixel size. */
data class ImportedPage(val fileName: String, val widthPx: Int, val heightPx: Int)

// Render resolution: 2x the page's point size (144 dpi) stays sharp when zoomed in on a phone.
private const val PDF_RENDER_SCALE = 2

// Same cap as inserted images.
private const val MAX_PAGE_EDGE_PX = 2048

// Bounds how much storage and time one import can take; 64 is on the §7 scale.
private const val MAX_IMPORTED_PAGES = 64

/**
 * Renders each page of the PDF at [uri] to a PNG in the shared images folder, so pages can be
 * placed on the canvas as ordinary images and annotated. Returns an empty list if the file
 * can't be opened (e.g. password-protected, which [PdfRenderer] doesn't support). Pages past
 * [MAX_IMPORTED_PAGES] are skipped.
 */
fun importPdfPages(context: Context, uri: Uri): List<ImportedPage> {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        return emptyList()
    }
    return renderer.use {
        (0 until minOf(renderer.pageCount, MAX_IMPORTED_PAGES)).map { index ->
            renderer.openPage(index).use { page ->
                // 2x the page size, but never over MAX_PAGE_EDGE_PX: a poster-size page at 2x would
                // need hundreds of MB.
                val scale = minOf(PDF_RENDER_SCALE.toFloat(), MAX_PAGE_EDGE_PX.toFloat() / maxOf(page.width, page.height))
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                // PDF pages are transparent where nothing is printed; paper is white.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val fileName = "${UUID.randomUUID()}.png"
                CanvasStorage.imageFile(context, fileName).outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                ImportedPage(fileName, bitmap.width, bitmap.height).also { bitmap.recycle() }
            }
        }
    }
}
