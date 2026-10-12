package com.andene.fern.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** The resolutions slices can be exported at (roadmap item 128): 1x, @2x and @3x, as app and web assets use. */
val SLICE_SCALES = listOf(1, 2, 3)

/**
 * Each slice's file name: the frame's name (characters file systems refuse replaced, kept
 * unique) with the usual `@2x` / `@3x` suffix, so the set drops straight into an app's or web
 * page's assets. In frame order, each frame's scales smallest first.
 */
fun sliceFileNames(frames: List<Frame>, scales: List<Int>): List<Triple<Frame, Int, String>> {
    val used = HashSet<String>()
    val bases = frames.map { frame ->
        val clean = frame.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Frame" }
        var base = clean
        var n = 2
        while (!used.add(base.lowercase())) base = "$clean ${n++}"
        frame to base
    }
    return bases.flatMap { (frame, base) ->
        scales.sorted().map { scale -> Triple(frame, scale, if (scale == 1) "$base.png" else "$base@${scale}x.png") }
    }
}

/**
 * Slices (roadmap item 128; Affinity's export persona): every [frames] as a PNG at each of
 * [scales], in one ZIP. At 1x a frame is one pixel per world unit - its size on screen at 100%
 * zoom - and @2x and @3x double and triple that. A slice whose longest edge would pass
 * [EXPORT_MAX_EDGE_PX] is made at that size instead, like every image export, so a huge frame
 * can't run out of memory. Null when there are no frames or scales.
 */
fun exportSlicesZip(
    source: CanvasState,
    frames: List<Frame>,
    scales: List<Int>,
    textMeasurer: TextMeasurer,
    density: Density,
    imageFor: (String) -> ImageBitmap?,
    transparent: Boolean = false,
): ByteArray? {
    if (frames.isEmpty() || scales.isEmpty()) return null
    val zip = ByteArrayOutputStream()
    ZipOutputStream(zip).use { out ->
        for ((frame, scale, name) in sliceFileNames(frames, scales)) {
            val edge = (maxOf(frame.max.x - frame.min.x, frame.max.y - frame.min.y) * scale).roundToInt().coerceIn(1, EXPORT_MAX_EDGE_PX)
            val framed = frameForExport(source, edge, frame) ?: continue
            val image = ImageBitmap(framed.widthPx, framed.heightPx)
            framed.drawOn(Canvas(image), density, textMeasurer, imageFor, transparent)
            out.putNextEntry(ZipEntry(name))
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            out.closeEntry()
        }
    }
    return zip.toByteArray()
}
