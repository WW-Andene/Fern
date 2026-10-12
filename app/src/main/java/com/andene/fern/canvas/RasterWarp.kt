package com.andene.fern.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.floor

/** Grid of the bitmap mesh a raster warp is drawn with: fine enough that the Bezier lattice looks smooth. */
private const val WARP_GRID = 16

/** Warped pixels: [pixels] (non-premultiplied ARGB) [width] x [height] placed at pixel ([left], [top]). */
class WarpedPixels(val pixels: IntArray, val width: Int, val height: Int, val left: Int, val top: Int)

/**
 * The pixels of [source] (non-premultiplied ARGB, [width] x [height], its top-left at pixel
 * ([left], [top]) of [surface]) bent by [warp], a world-space mapping: the mesh lattice
 * ([meshWarp], roadmap item 92), a corner distort or a puppet warp (item 122). The picture is
 * drawn with the platform's bitmap mesh: [warp] is evaluated at a [WARP_GRID] grid and each
 * cell stretched bilinearly between, at 8 bits per channel.
 */
fun warpPixels(surface: RasterSurface, source: IntArray, width: Int, height: Int, left: Int, top: Int, warp: (WorldPoint) -> WorldPoint): WarpedPixels? {
    val verts = FloatArray((WARP_GRID + 1) * (WARP_GRID + 1) * 2)
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for (j in 0..WARP_GRID) for (i in 0..WARP_GRID) {
        val world = surface.worldOf(left + width * i.toDouble() / WARP_GRID, top + height * j.toDouble() / WARP_GRID)
        val moved = warp(world)
        val x = (moved.x - surface.originX) / surface.pixelWorld
        val y = (moved.y - surface.originY) / surface.pixelWorld
        verts[(j * (WARP_GRID + 1) + i) * 2] = x.toFloat()
        verts[(j * (WARP_GRID + 1) + i) * 2 + 1] = y.toFloat()
        minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
    }
    val destLeft = floor(minX).toInt()
    val destTop = floor(minY).toInt()
    val destWidth = (ceil(maxX).toInt() - destLeft + 1).coerceAtMost(MAX_WARP_EDGE)
    val destHeight = (ceil(maxY).toInt() - destTop + 1).coerceAtMost(MAX_WARP_EDGE)
    if (destWidth <= 0 || destHeight <= 0) return null
    for (k in verts.indices step 2) {
        verts[k] = verts[k] - destLeft
        verts[k + 1] = verts[k + 1] - destTop
    }
    val src = Bitmap.createBitmap(source, width, height, Bitmap.Config.ARGB_8888)
    val dest = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
    Canvas(dest).drawBitmapMesh(src, WARP_GRID, WARP_GRID, verts, 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG))
    val pixels = IntArray(destWidth * destHeight)
    dest.getPixels(pixels, 0, destWidth, 0, 0, destWidth, destHeight)
    src.recycle()
    dest.recycle()
    return WarpedPixels(pixels, destWidth, destHeight, destLeft, destTop)
}

// Largest warped picture, in pixels per edge: a lattice dragged far away can't allocate without bound.
private const val MAX_WARP_EDGE = 4096
