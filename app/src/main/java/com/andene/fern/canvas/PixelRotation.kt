package com.andene.fern.canvas

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Rotating pixel art (roadmap item 145). Plain nearest-neighbour rotation breaks one-pixel lines
 * into stairs and holes; RotSprite (Xenowhirl's algorithm, as Aseprite uses it) first enlarges
 * the sprite 8x with Scale2x three times - which smooths diagonal edges without inventing new
 * colours - then rotates that with nearest-neighbour sampling straight back down to 1x, so each
 * output pixel takes one of the sprite's own colours from the right side of every edge.
 */

/** A rectangle of ARGB pixels, by row. */
class PixelImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(pixels.size == width * height) { "Pixel count doesn't match the size" } }

    fun at(x: Int, y: Int): Int = pixels[y * width + x]
}

/**
 * Scale2x (EPX): each pixel becomes four, each corner taking a neighbour's colour where the two
 * neighbours around that corner agree and the opposite ones don't, the pixel's own otherwise.
 */
fun scale2x(image: PixelImage): PixelImage {
    val w = image.width
    val h = image.height
    val out = IntArray(w * 2 * h * 2)
    for (y in 0 until h) for (x in 0 until w) {
        val p = image.at(x, y)
        val a = if (y > 0) image.at(x, y - 1) else p // above
        val b = if (x < w - 1) image.at(x + 1, y) else p // right
        val c = if (x > 0) image.at(x - 1, y) else p // left
        val d = if (y < h - 1) image.at(x, y + 1) else p // below
        var e0 = p; var e1 = p; var e2 = p; var e3 = p
        if (c == a && c != d && a != b) e0 = a
        if (a == b && a != c && b != d) e1 = b
        if (d == c && d != b && c != a) e2 = c
        if (b == d && b != a && d != c) e3 = d
        val row = y * 2 * w * 2
        out[row + 2 * x] = e0
        out[row + 2 * x + 1] = e1
        out[row + w * 2 + 2 * x] = e2
        out[row + w * 2 + 2 * x + 1] = e3
    }
    return PixelImage(w * 2, h * 2, out)
}

/**
 * [image] turned [degrees] clockwise (y down) about its centre, onto a canvas just big enough to
 * hold it (transparent around it), with RotSprite ([rotSprite] true) or plain nearest-neighbour
 * sampling. The result's centre is the source's centre.
 */
fun rotatePixels(image: PixelImage, degrees: Double, rotSprite: Boolean): PixelImage {
    val radians = Math.toRadians(degrees)
    val c = cos(radians)
    val s = sin(radians)
    // Right angles are exact turns: no resampling, no RotSprite needed.
    val quarter = Math.round(degrees / 90.0)
    if (abs(degrees - quarter * 90.0) < 1e-9) return rotateQuarter(image, Math.floorMod(quarter, 4L).toInt())
    val outWidth = ceil(abs(image.width * c) + abs(image.height * s) - 1e-9).toInt().coerceAtLeast(1)
    val outHeight = ceil(abs(image.width * s) + abs(image.height * c) - 1e-9).toInt().coerceAtLeast(1)
    val source = if (rotSprite) scale2x(scale2x(scale2x(image))) else image
    val factor = source.width.toDouble() / image.width
    val out = IntArray(outWidth * outHeight)
    for (y in 0 until outHeight) for (x in 0 until outWidth) {
        // Inverse-rotate this pixel's centre into the source, in source pixels.
        val dx = x + 0.5 - outWidth / 2.0
        val dy = y + 0.5 - outHeight / 2.0
        val sx = (c * dx + s * dy + image.width / 2.0) * factor
        val sy = (-s * dx + c * dy + image.height / 2.0) * factor
        val ix = floor(sx).toInt()
        val iy = floor(sy).toInt()
        if (ix in 0 until source.width && iy in 0 until source.height) out[y * outWidth + x] = source.at(ix, iy)
    }
    return PixelImage(outWidth, outHeight, out)
}

private fun rotateQuarter(image: PixelImage, turns: Int): PixelImage {
    val w = image.width
    val h = image.height
    return when (turns) {
        0 -> PixelImage(w, h, image.pixels.copyOf())
        1 -> PixelImage(h, w, IntArray(w * h) { k -> val x = k % h; val y = k / h; image.at(y, h - 1 - x) })
        2 -> PixelImage(w, h, IntArray(w * h) { k -> image.pixels[w * h - 1 - k] })
        else -> PixelImage(h, w, IntArray(w * h) { k -> val x = k % h; val y = k / h; image.at(w - 1 - y, x) })
    }
}
