package com.andene.fern.canvas

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * GPU rendering path for scene tiles (roadmap item 52). The canvas itself already draws through
 * Android's hardware pipeline; the slow part of a huge document is filling [SceneTileCache],
 * which rasterized every tile on the CPU into a software bitmap (~4.6 s for a whole 1M-stroke
 * view, ScaleBenchmark). Here a tile's drawing is recorded into a [RenderNode], rasterized by
 * the GPU through a [HardwareRenderer] into an [ImageReader], and kept as a hardware bitmap -
 * it stays in GPU memory, so drawing it each frame needs no upload either.
 *
 * API 29+ (HardwareRenderer, hardware-buffer bitmaps). Any failure (no GPU context, an
 * emulator or test environment without one) reports null once and the caller keeps using the
 * CPU path for the rest of the session. Not yet measured on a device.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class GpuTileRenderer(private val sizePx: Int) {
    private val reader = ImageReader.newInstance(
        sizePx, sizePx, PixelFormat.RGBA_8888, MAX_IMAGES,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    private val node = RenderNode("tile").apply { setPosition(0, 0, sizePx, sizePx) }
    private val renderer = HardwareRenderer().apply {
        setContentRoot(node)
        setSurface(reader.surface)
    }

    /** Renders [draw] into a new hardware-backed tile, or null if the GPU path failed. */
    fun render(density: Density, draw: DrawScope.() -> Unit): ImageBitmap? = runCatching {
        val recording = node.beginRecording(sizePx, sizePx)
        try {
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(recording), Size(sizePx.toFloat(), sizePx.toFloat()), draw)
        } finally {
            node.endRecording()
        }
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        val image = reader.acquireNextImage() ?: return@runCatching null
        try {
            val buffer = image.hardwareBuffer ?: return@runCatching null
            // Wrapping keeps a reference to the buffer, so the image can be closed straight away.
            val bitmap = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
            buffer.close()
            bitmap?.asImageBitmap()
        } finally {
            image.close()
        }
    }.getOrNull()

    fun release() {
        renderer.destroy()
        reader.close()
    }

    private companion object {
        // Images the reader holds before acquireNextImage; one per render, closed right away.
        const val MAX_IMAGES = 2
    }
}
