package com.andene.fern.canvas

import android.graphics.Bitmap
import android.util.Half
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ShortBuffer
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/** Edge of a raster tile in pixels; on the §7 scale. */
const val RASTER_TILE = 256

/**
 * One tile of a raster layer: [RASTER_TILE] x [RASTER_TILE] pixels of premultiplied RGBA at
 * 16 bits per channel (0..65535), whatever the layer's depth - an 8-bit layer is rounded to
 * 8 bits when it's saved and shown, so painting itself never loses precision mid-stroke.
 */
class RasterTile(val data: ShortArray = ShortArray(RASTER_TILE * RASTER_TILE * 4)) {
    /** Bumped on every change; the cached bitmap is rebuilt when it's out of date. */
    var revision = 0
        internal set

    internal var bitmap: Bitmap? = null
    internal var bitmapRevision = -1

    fun copy(): RasterTile = RasterTile(data.copyOf())

    fun isEmpty(): Boolean {
        for (i in 3 until data.size step 4) if (data[i].toInt() != 0) return false
        return true
    }
}

/**
 * The pixels of a raster layer (roadmap item 89): an unbounded grid of [RasterTile]s, created
 * only where something is painted, so a raster layer on an infinite canvas costs memory only
 * where it has paint. Pixels are squares [pixelWorld] world units wide, laid from the world
 * point [originX], [originY] (moved by a floating-origin rebase, never resampled).
 *
 * [depth] is 8 or 16 bits per channel: what the layer is saved and displayed at (16-bit shows
 * through half-float bitmaps, so gradients don't band).
 */
class RasterSurface(val depth: Int, val pixelWorld: Double, var originX: Double = 0.0, var originY: Double = 0.0) {
    init {
        require(depth == 8 || depth == 16) { "Depth is 8 or 16 bits" }
        require(pixelWorld > 0.0)
    }

    val tiles = HashMap<Long, RasterTile>()

    /** Bumped on every change, so views redraw and caches notice. */
    var revision = 0
        private set

    /** Called with a tile's key just before it first changes (it may not exist yet), for undo. */
    var beforeTileChange: (Long) -> Unit = {}

    /** Called with a tile's key after it changed, for incremental saving. */
    var afterTileChange: (Long) -> Unit = {}

    fun tileAt(key: Long): RasterTile? = tiles[key]

    /** Puts [tile] at [key], or removes it when null (undo, loading). */
    fun setTile(key: Long, tile: RasterTile?) {
        if (tile == null) tiles.remove(key) else tiles[key] = tile
        tile?.revision = (tile?.revision ?: 0) + 1
        revision++
        afterTileChange(key)
    }

    /** A copy sharing tile data but with its own origin, for an export camera that may rebase. */
    fun view(): RasterSurface = RasterSurface(depth, pixelWorld, originX, originY).also { it.tiles.putAll(tiles) }

    fun shiftOrigin(offset: WorldPoint) {
        originX -= offset.x
        originY -= offset.y
        revision++
    }

    /** World position of pixel corner ([px], [py]). */
    fun worldOf(px: Double, py: Double) = WorldPoint(originX + px * pixelWorld, originY + py * pixelWorld)

    /**
     * Paints one round dab centred on world point [center], [radius] world units, in [color]
     * (non-premultiplied RGBA, 0..1 each) at [opacity]. [hardness] 1 is a hard edge; lower values
     * fade from (hardness x radius) out to the rim. [erase] removes paint instead (destination-out).
     */
    fun dab(center: WorldPoint, radius: Double, color: FloatArray, opacity: Float, hardness: Float = 0.8f, erase: Boolean = false, tip: TipMask? = null) {
        val cx = (center.x - originX) / pixelWorld
        val cy = (center.y - originY) / pixelWorld
        val r = (radius / pixelWorld).coerceAtLeast(0.5)
        forEachPixelIn(cx - r, cy - r, cx + r, cy + r) { tile, index, px, py ->
            val falloff = coverage(px, py, cx, cy, r, hardness, tip)
            if (falloff <= 0.0) return@forEachPixelIn
            val a = (opacity * falloff).coerceIn(0.0, 1.0)
            if (erase) {
                for (c in 0 until 4) tile.data[index + c] = (u16(tile.data[index + c]) * (1 - a)).toInt().toShort()
            } else {
                val srcA = color[3] * a
                for (c in 0 until 3) {
                    val src = color[c] * srcA * 65535.0
                    tile.data[index + c] = (src + u16(tile.data[index + c]) * (1 - srcA)).toInt().coerceIn(0, 65535).toShort()
                }
                tile.data[index + 3] = (srcA * 65535.0 + u16(tile.data[index + 3]) * (1 - srcA)).toInt().coerceIn(0, 65535).toShort()
            }
        }
    }

    /** How much of a dab centred on ([cx], [cy]) with radius [r] (pixels) covers pixel ([px], [py]). */
    private fun coverage(px: Int, py: Int, cx: Double, cy: Double, r: Double, hardness: Float, tip: TipMask?): Double =
        if (tip != null) tip.coverageAt((px + 0.5 - cx) / r, (py + 0.5 - cy) / r)
        else dabFalloff(px + 0.5, py + 0.5, cx, cy, r, hardness.toDouble())

    /**
     * The average colour (non-premultiplied RGBA, 0..1) under a dab at [center] - what a wet
     * brush picks up. Transparent where there's no paint.
     */
    fun sampleUnder(center: WorldPoint, radius: Double): FloatArray {
        val cx = (center.x - originX) / pixelWorld
        val cy = (center.y - originY) / pixelWorld
        val r = (radius / pixelWorld).coerceAtLeast(0.5)
        val sum = DoubleArray(4)
        var weight = 0.0
        val step = maxOf(1, (r / 8).toInt()) // a sparse grid is enough for an average
        var py = floor(cy - r).toInt()
        while (py <= ceil(cy + r)) {
            var px = floor(cx - r).toInt()
            while (px <= ceil(cx + r)) {
                if (hypot(px + 0.5 - cx, py + 0.5 - cy) <= r) {
                    val p = pixel(px, py)
                    for (c in 0 until 4) sum[c] = sum[c] + p[c]
                    weight++
                }
                px += step
            }
            py += step
        }
        if (weight == 0.0 || sum[3] == 0.0) return FloatArray(4)
        // Premultiplied sums: divide the colour by the coverage to unpremultiply.
        return floatArrayOf((sum[0] / sum[3]).toFloat(), (sum[1] / sum[3]).toFloat(), (sum[2] / sum[3]).toFloat(), (sum[3] / weight).toFloat())
    }

    /**
     * Smudge: the pixels under a dab at [to] become a mix of themselves and the pixels one step
     * back along the stroke (at [from]), by [strength] times the brush's coverage, dragging
     * paint along. Reads a copy of the source first, so the dab never smears its own output.
     */
    fun smudge(from: WorldPoint, to: WorldPoint, radius: Double, strength: Float, tip: TipMask? = null) {
        val cx = (to.x - originX) / pixelWorld
        val cy = (to.y - originY) / pixelWorld
        val dx = (to.x - from.x) / pixelWorld
        val dy = (to.y - from.y) / pixelWorld
        val r = (radius / pixelWorld).coerceAtLeast(0.5)
        val source = HashMap<Long, FloatArray>()
        for (py in floor(cy - r).toInt()..ceil(cy + r).toInt()) for (px in floor(cx - r).toInt()..ceil(cx + r).toInt()) {
            source[RasterSurface.tileKey(px, py)] = pixel(Math.round(px - dx).toInt(), Math.round(py - dy).toInt())
        }
        forEachPixelIn(cx - r, cy - r, cx + r, cy + r) { tile, index, px, py ->
            val a = strength * coverage(px, py, cx, cy, r, 0.5f, tip)
            if (a <= 0.0) return@forEachPixelIn
            val s = source[RasterSurface.tileKey(px, py)] ?: return@forEachPixelIn
            for (c in 0 until 4) tile.data[index + c] = (u16(tile.data[index + c]) * (1 - a) + s[c] * 65535.0 * a).toInt().coerceIn(0, 65535).toShort()
        }
    }

    /** Blur: pixels under a dab move toward the average of their 3 x 3 neighbourhood, by [strength] times the coverage. */
    fun blur(center: WorldPoint, radius: Double, strength: Float, tip: TipMask? = null) {
        val cx = (center.x - originX) / pixelWorld
        val cy = (center.y - originY) / pixelWorld
        val r = (radius / pixelWorld).coerceAtLeast(0.5)
        val averaged = HashMap<Long, FloatArray>()
        for (py in floor(cy - r).toInt()..ceil(cy + r).toInt()) for (px in floor(cx - r).toInt()..ceil(cx + r).toInt()) {
            val sum = FloatArray(4)
            for (oy in -1..1) for (ox in -1..1) {
                val p = pixel(px + ox, py + oy)
                for (c in 0 until 4) sum[c] += p[c] / 9f
            }
            averaged[RasterSurface.tileKey(px, py)] = sum
        }
        forEachPixelIn(cx - r, cy - r, cx + r, cy + r) { tile, index, px, py ->
            val a = strength * coverage(px, py, cx, cy, r, 0.5f, tip)
            if (a <= 0.0) return@forEachPixelIn
            val s = averaged[RasterSurface.tileKey(px, py)] ?: return@forEachPixelIn
            for (c in 0 until 4) tile.data[index + c] = (u16(tile.data[index + c]) * (1 - a) + s[c] * 65535.0 * a).toInt().coerceIn(0, 65535).toShort()
        }
    }

    /**
     * Visits every pixel in the pixel-space box, creating tiles as needed, with the tile, the
     * pixel's index into its data, and the pixel's coordinates. Each touched tile is announced
     * through [beforeTileChange] once, before it changes, and [afterTileChange] after.
     */
    fun forEachPixelIn(left: Double, top: Double, right: Double, bottom: Double, visit: (RasterTile, Int, Int, Int) -> Unit) {
        val x0 = floor(left).toInt()
        val y0 = floor(top).toInt()
        val x1 = ceil(right).toInt()
        val y1 = ceil(bottom).toInt()
        for (ty in Math.floorDiv(y0, RASTER_TILE)..Math.floorDiv(y1, RASTER_TILE)) for (tx in Math.floorDiv(x0, RASTER_TILE)..Math.floorDiv(x1, RASTER_TILE)) {
            val key = tileKey(tx, ty)
            beforeTileChange(key)
            val tile = tiles.getOrPut(key) { RasterTile() }
            for (py in maxOf(y0, ty * RASTER_TILE) until minOf(y1 + 1, (ty + 1) * RASTER_TILE)) {
                for (px in maxOf(x0, tx * RASTER_TILE) until minOf(x1 + 1, (tx + 1) * RASTER_TILE)) {
                    visit(tile, ((py - ty * RASTER_TILE) * RASTER_TILE + (px - tx * RASTER_TILE)) * 4, px, py)
                }
            }
            tile.revision++
            afterTileChange(key)
        }
        revision++
    }

    /** Premultiplied RGBA (0..1) of pixel ([px], [py]); transparent where nothing was painted. */
    fun pixel(px: Int, py: Int): FloatArray {
        val tile = tiles[tileKey(Math.floorDiv(px, RASTER_TILE), Math.floorDiv(py, RASTER_TILE))] ?: return FloatArray(4)
        val index = (Math.floorMod(py, RASTER_TILE) * RASTER_TILE + Math.floorMod(px, RASTER_TILE)) * 4
        return FloatArray(4) { u16(tile.data[index + it]).toFloat() / 65535f }
    }

    /** The tile's pixels as a bitmap at the layer's depth, rebuilt only after it changed. */
    fun bitmapOf(tile: RasterTile): Bitmap {
        tile.bitmap?.let { if (tile.bitmapRevision == tile.revision) return it }
        val bitmap = tile.bitmap ?: Bitmap.createBitmap(RASTER_TILE, RASTER_TILE, if (depth == 16) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888)
        if (depth == 16) {
            // Half floats, premultiplied, as RGBA_F16 stores them.
            val halves = ShortArray(tile.data.size) { Half.toHalf(u16(tile.data[it]) / 65535f) }
            bitmap.copyPixelsFromBuffer(ShortBuffer.wrap(halves))
        } else {
            // setPixels takes unpremultiplied ARGB.
            val argb = IntArray(RASTER_TILE * RASTER_TILE) { i ->
                val a = u16(tile.data[i * 4 + 3])
                if (a == 0) 0 else {
                    fun channel(c: Int) = (u16(tile.data[i * 4 + c]).toLong() * 255 / a).toInt().coerceIn(0, 255)
                    ((a * 255 / 65535) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
                }
            }
            bitmap.setPixels(argb, 0, RASTER_TILE, 0, 0, RASTER_TILE, RASTER_TILE)
        }
        tile.bitmap = bitmap
        tile.bitmapRevision = tile.revision
        return bitmap
    }

    companion object {
        fun tileKey(tx: Int, ty: Int): Long = (tx.toLong() shl 32) or (ty.toLong() and 0xFFFFFFFFL)
        fun tileX(key: Long): Int = (key shr 32).toInt()
        fun tileY(key: Long): Int = key.toInt()

        internal fun u16(value: Short): Int = value.toInt() and 0xFFFF

        /** Writes one tile's pixels, compressed, at [depth] bits per channel. */
        fun writeTile(out: DataOutputStream, tile: RasterTile, depth: Int) {
            val raw = java.io.ByteArrayOutputStream()
            DataOutputStream(DeflaterOutputStream(raw)).use { z ->
                if (depth == 16) for (v in tile.data) z.writeShort(v.toInt()) else for (v in tile.data) z.writeByte(u16(v) shr 8)
            }
            val bytes = raw.toByteArray()
            out.writeInt(bytes.size)
            out.write(bytes)
        }

        fun readTile(input: DataInputStream, depth: Int): RasterTile {
            val length = input.readInt()
            if (length < 0 || length > MAX_TILE_BYTES) throw IOException("Bad raster tile length $length")
            val bytes = ByteArray(length).also { input.readFully(it) }
            val tile = RasterTile()
            DataInputStream(InflaterInputStream(bytes.inputStream())).use { z ->
                for (i in tile.data.indices) {
                    tile.data[i] = if (depth == 16) z.readShort() else (z.readUnsignedByte() * 257).toShort()
                }
            }
            return tile
        }

        // A tile is 512 KB raw at 16 bits; compressed it's never larger than that plus overhead.
        private const val MAX_TILE_BYTES = RASTER_TILE * RASTER_TILE * 8 + 4096
    }
}

/** The dabs of a painted segment from [a] to [b], [spacing] world units apart, the first on [a]. */
fun dabsAlong(a: WorldPoint, b: WorldPoint, spacing: Double, startOffset: Double = 0.0): List<WorldPoint> {
    val length = hypot(b.x - a.x, b.y - a.y)
    if (spacing <= 0.0) return listOf(b)
    val result = ArrayList<WorldPoint>()
    var t = startOffset
    while (t <= length) {
        val f = if (length == 0.0) 0.0 else t / length
        result.add(WorldPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f))
        t += spacing
    }
    return result
}
