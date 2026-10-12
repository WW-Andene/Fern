package com.andene.fern.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Space left between packed sprites, so texture filtering never bleeds one into the next. */
private const val ATLAS_PADDING_PX = 2

/** Where each rectangle went in a packed sheet ([x], [y] per input, in order) and the sheet's size, both powers of two. */
class PackedSheet(val x: IntArray, val y: IntArray, val width: Int, val height: Int)

/**
 * Packs [sizes] (width, height) into one sheet with [padding] between them: shelf packing,
 * tallest first, tried at every power-of-two width from the narrowest that fits the widest
 * sprite; the smallest resulting sheet wins. Sheets are powers of two on both sides, which
 * older GPUs and some engines require.
 */
fun packRects(sizes: List<Pair<Int, Int>>, padding: Int = ATLAS_PADDING_PX): PackedSheet {
    val order = sizes.indices.sortedByDescending { sizes[it].second }
    val widest = (sizes.maxOfOrNull { it.first } ?: 1) + padding
    val totalArea = sizes.sumOf { (w, h) -> (w + padding).toLong() * (h + padding) }
    var best: PackedSheet? = null
    var width = nextPowerOfTwo(widest)
    while (true) {
        val x = IntArray(sizes.size)
        val y = IntArray(sizes.size)
        var shelfY = 0
        var shelfHeight = 0
        var cursor = 0
        for (i in order) {
            val (w, h) = sizes[i]
            if (cursor + w + padding > width && cursor > 0) {
                shelfY += shelfHeight
                shelfHeight = 0
                cursor = 0
            }
            x[i] = cursor
            y[i] = shelfY
            cursor += w + padding
            shelfHeight = maxOf(shelfHeight, h + padding)
        }
        val sheet = PackedSheet(x, y, width, nextPowerOfTwo(shelfY + shelfHeight))
        if (best == null || sheet.width.toLong() * sheet.height < best.width.toLong() * best.height) best = sheet
        // Wider than square past the total area can only waste space.
        if (width.toLong() * width >= totalArea * 4 || width >= MAX_ATLAS_EDGE_PX) break
        width *= 2
    }
    return best!!
}

private fun nextPowerOfTwo(n: Int): Int {
    var p = 1
    while (p < n) p = p shl 1
    return p
}

private const val MAX_ATLAS_EDGE_PX = 8192

/**
 * Every animation frame as a packed sprite atlas (roadmap item 136, what TexturePacker makes
 * for game engines): each frame drawn on a transparent background, trimmed to its own ink, and
 * packed tightly onto one PNG, with a JSON description in TexturePacker's "JSON hash" layout -
 * which Phaser, PixiJS, Cocos and many engines read - giving each sprite's place on the sheet,
 * where the trimmed part sat in the full frame (so the animation doesn't jitter) and how long
 * it shows (the playback speed and X-sheet holds). Returned as a ZIP of `<name>.png` and
 * `<name>.json`; null for an empty document.
 */
fun exportSpriteAtlas(source: CanvasState, name: String, textMeasurer: TextMeasurer, density: Density, imageFor: (String) -> ImageBitmap?): ByteArray? {
    val frame = frameForExport(source, SPRITE_FRAME_EDGE_PX) ?: return null
    val count = source.animFrameCount
    // Each frame's trimmed pixels and where they sat in the full frame.
    val sprites = (0 until count).map { i ->
        frame.camera.goToAnimFrame(i)
        val image = ImageBitmap(frame.widthPx, frame.heightPx)
        frame.drawOn(Canvas(image), density, textMeasurer, imageFor, transparent = true)
        trimmed(image.asAndroidBitmap())
    }
    val sheet = packRects(sprites.map { it.bitmap.width to it.bitmap.height })
    val atlas = Bitmap.createBitmap(sheet.width, sheet.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(atlas)
    val frames = JSONObject()
    val frameMillis = 1000.0 / source.animFps.coerceAtLeast(1)
    for ((i, sprite) in sprites.withIndex()) {
        canvas.drawBitmap(sprite.bitmap, sheet.x[i].toFloat(), sheet.y[i].toFloat(), null)
        frames.put(
            String.format(Locale.ROOT, "%s_%03d.png", name, i),
            JSONObject()
                .put("frame", rect(sheet.x[i], sheet.y[i], sprite.bitmap.width, sprite.bitmap.height))
                .put("rotated", false)
                .put("trimmed", sprite.bitmap.width != frame.widthPx || sprite.bitmap.height != frame.heightPx)
                .put("spriteSourceSize", rect(sprite.left, sprite.top, sprite.bitmap.width, sprite.bitmap.height))
                .put("sourceSize", JSONObject().put("w", frame.widthPx).put("h", frame.heightPx))
                .put("duration", Math.round(frameMillis * source.holdOf(i))),
        )
    }
    val json = JSONObject()
        .put("frames", frames)
        .put(
            "meta",
            JSONObject()
                .put("app", "Fern")
                .put("version", "1.0")
                .put("image", "$name.png")
                .put("format", "RGBA8888")
                .put("size", JSONObject().put("w", sheet.width).put("h", sheet.height))
                .put("scale", "1"),
        )
    val zip = ByteArrayOutputStream()
    ZipOutputStream(zip).use { out ->
        out.putNextEntry(ZipEntry("$name.png"))
        atlas.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.closeEntry()
        out.putNextEntry(ZipEntry("$name.json"))
        out.write(json.toString(2).toByteArray())
        out.closeEntry()
    }
    return zip.toByteArray()
}

private class TrimmedSprite(val bitmap: Bitmap, val left: Int, val top: Int)

/** [full] cut down to the box around its visible pixels; an empty frame becomes one clear pixel. */
private fun trimmed(full: Bitmap): TrimmedSprite {
    val w = full.width
    val h = full.height
    val pixels = IntArray(w * h).also { full.getPixels(it, 0, w, 0, 0, w, h) }
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) for (x in 0 until w) {
        if ((pixels[y * w + x] ushr 24) != 0) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return TrimmedSprite(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 0, 0)
    return TrimmedSprite(Bitmap.createBitmap(full, minX, minY, maxX - minX + 1, maxY - minY + 1), minX, minY)
}

private fun rect(x: Int, y: Int, w: Int, h: Int) = JSONObject().put("x", x).put("y", y).put("w", w).put("h", h)
