package com.andene.fern.canvas

import java.io.ByteArrayOutputStream

/**
 * Minimal animated GIF89a encoder (Android has none built in). Every frame uses one fixed
 * global palette - the 216-colour web-safe cube plus greys - and nearest-colour mapping. That
 * suits line art and flat colour well; smooth gradients will band, the usual GIF tradeoff.
 * Frames loop forever.
 *
 * [frames] are ARGB pixel arrays of [width] x [height]; [delayCentis] is the per-frame delay
 * in hundredths of a second (the GIF unit).
 */
fun encodeGif(frames: List<IntArray>, width: Int, height: Int, delayCentis: Int): ByteArray {
    val out = ByteArrayOutputStream()
    fun short(value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    out.write("GIF89a".toByteArray())
    short(width)
    short(height)
    out.write(0xF7) // global colour table present, 8-bit colour resolution, 256 entries
    out.write(0) // background colour index
    out.write(0) // no aspect ratio
    for (rgb in GIF_PALETTE) {
        out.write((rgb shr 16) and 0xFF)
        out.write((rgb shr 8) and 0xFF)
        out.write(rgb and 0xFF)
    }

    // Netscape extension: loop forever.
    out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
    out.write("NETSCAPE2.0".toByteArray())
    out.write(byteArrayOf(3, 1, 0, 0, 0))

    for (pixels in frames) {
        // Graphic control extension: frame delay, no transparency.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0))
        short(delayCentis)
        out.write(byteArrayOf(0, 0))
        // Image descriptor: full-canvas frame, no local colour table.
        out.write(0x2C)
        short(0)
        short(0)
        short(width)
        short(height)
        out.write(0)
        val indices = ByteArray(pixels.size) { paletteIndex(pixels[it]).toByte() }
        writeLzw(out, indices)
    }
    out.write(0x3B) // trailer
    return out.toByteArray()
}

// 6x6x6 colour cube (216) + 40 greys = 256 entries.
private val GIF_PALETTE: IntArray = IntArray(256).also { palette ->
    var i = 0
    for (r in 0 until 6) for (g in 0 until 6) for (b in 0 until 6) {
        palette[i++] = (r * 51 shl 16) or (g * 51 shl 8) or (b * 51)
    }
    for (k in 0 until 40) {
        val v = (k * 255) / 39
        palette[i++] = (v shl 16) or (v shl 8) or v
    }
}

/** Nearest palette entry: the closer of the nearest cube colour and the nearest grey. */
private fun paletteIndex(argb: Int): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val ri = (r + 25) / 51
    val gi = (g + 25) / 51
    val bi = (b + 25) / 51
    val cube = ri * 36 + gi * 6 + bi
    val cubeError = sq(r - ri * 51) + sq(g - gi * 51) + sq(b - bi * 51)
    val greyLevel = ((r + g + b) / 3 * 39 + 127) / 255
    val grey = (greyLevel * 255) / 39
    val greyError = sq(r - grey) + sq(g - grey) + sq(b - grey)
    return if (greyError < cubeError) 216 + greyLevel else cube
}

private fun sq(x: Int) = x * x

/** GIF-flavoured LZW compression of 8-bit [indices], written as data sub-blocks. */
private fun writeLzw(out: ByteArrayOutputStream, indices: ByteArray) {
    val minCodeSize = 8
    val clearCode = 1 shl minCodeSize
    val endCode = clearCode + 1
    out.write(minCodeSize)

    val block = ByteArray(255)
    var blockLength = 0
    var bitBuffer = 0
    var bitCount = 0
    fun emit(code: Int, size: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += size
        while (bitCount >= 8) {
            block[blockLength++] = (bitBuffer and 0xFF).toByte()
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
            if (blockLength == 255) {
                out.write(255)
                out.write(block, 0, 255)
                blockLength = 0
            }
        }
    }

    // Dictionary keyed by (prefix code shl 8) or next byte.
    val dictionary = HashMap<Int, Int>()
    var codeSize = minCodeSize + 1
    var nextCode = endCode + 1
    emit(clearCode, codeSize)
    if (indices.isEmpty()) {
        emit(endCode, codeSize)
    } else {
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val byte = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or byte
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            emit(prefix, codeSize)
            if (nextCode < 4096) {
                dictionary[key] = nextCode++
                // Grow the code size once the next code no longer fits.
                if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                // Table full: reset, as the GIF spec allows.
                emit(clearCode, codeSize)
                dictionary.clear()
                codeSize = minCodeSize + 1
                nextCode = endCode + 1
            }
            prefix = byte
        }
        emit(prefix, codeSize)
        // The decoder adds one more table entry after this last code; if that fills the
        // current code size, it reads the end code one bit wider (as in the classic
        // compress/GIF encoder, which checks the size after every code it outputs).
        if (nextCode >= (1 shl codeSize) && codeSize < 12) codeSize++
        emit(endCode, codeSize)
    }
    if (bitCount > 0) {
        block[blockLength++] = (bitBuffer and 0xFF).toByte()
        if (blockLength == 255) {
            out.write(255)
            out.write(block, 0, 255)
            blockLength = 0
        }
    }
    if (blockLength > 0) {
        out.write(blockLength)
        out.write(block, 0, blockLength)
    }
    out.write(0) // block terminator
}
