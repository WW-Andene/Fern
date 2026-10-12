package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

private const val BLOCK_COLOR = 0x0001

/** True if [bytes] start with the Adobe Swatch Exchange signature, "ASEF". */
fun isAdobeSwatchExchange(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 'A'.code.toByte() && bytes[1] == 'S'.code.toByte() &&
        bytes[2] == 'E'.code.toByte() && bytes[3] == 'F'.code.toByte()

/**
 * Colours from an Adobe Swatch Exchange (.ase) palette, the swatch format shared by Photoshop,
 * Illustrator, InDesign and many palette sites. Layout (big-endian): "ASEF", version (two
 * shorts), block count (int), then blocks of type (short) + length (int) + body. Colour blocks
 * hold a UTF-16 name, a colour model ("RGB ", "CMYK", "LAB " or "Gray") and its float values;
 * group start/end blocks are skipped (their colours still come through, flattened).
 *
 * Every block is skipped by its declared length, so unknown block types and extra fields don't
 * derail parsing; a truncated file returns the colours read before the damage. Returns an empty
 * list if the signature is missing. Conversions to sRGB:
 * - CMYK: the uncalibrated `(1 - c)(1 - k)` formula - no ICC profile is available, so print
 *   colours are approximate, as they are in most apps without colour management.
 * - LAB: CIE L*a*b* with a D50 white point (Adobe's convention), adapted to sRGB's D65 white by
 *   the Bradford transform.
 */
fun parseAdobeSwatchExchange(bytes: ByteArray): List<Color> {
    if (!isAdobeSwatchExchange(bytes)) return emptyList()
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    val colors = mutableListOf<Color>()
    try {
        buffer.position(4)
        buffer.short // major version
        buffer.short // minor version
        val blockCount = buffer.int
        repeat(blockCount) {
            val type = buffer.short.toInt() and 0xFFFF
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) return colors
            val end = buffer.position() + length
            if (type == BLOCK_COLOR) readColorBlock(buffer)?.let(colors::add)
            buffer.position(end)
        }
    } catch (e: BufferUnderflowException) {
        // Truncated file: keep what was read.
    }
    return colors
}

private fun readColorBlock(buffer: ByteBuffer): Color? {
    val nameLength = buffer.short.toInt() and 0xFFFF // UTF-16 code units, including the terminator
    buffer.position(buffer.position() + nameLength * 2)
    val model = String(ByteArray(4).also { buffer.get(it) }, Charsets.US_ASCII)
    return when (model) {
        "RGB " -> rgb(buffer.float, buffer.float, buffer.float)
        "CMYK" -> {
            val c = buffer.float
            val m = buffer.float
            val y = buffer.float
            val k = buffer.float
            rgb((1 - c) * (1 - k), (1 - m) * (1 - k), (1 - y) * (1 - k))
        }
        // L is stored as a fraction (0..1 = L* 0..100); a and b directly.
        "LAB " -> labToColor(buffer.float * 100.0, buffer.float.toDouble(), buffer.float.toDouble())
        "Gray" -> buffer.float.let { rgb(it, it, it) }
        else -> null
    }
}

private fun rgb(r: Float, g: Float, b: Float) = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))

/** CIE L*a*b* (D50) to sRGB: Lab -> XYZ (D50) -> Bradford to D65 -> linear sRGB -> gamma. */
internal fun labToColor(l: Double, a: Double, b: Double): Color {
    fun f(t: Double) = if (t > 6.0 / 29.0) t * t * t else 3 * (6.0 / 29.0).pow(2) * (t - 4.0 / 29.0)
    val fy = (l + 16) / 116
    // D50 reference white.
    val x50 = 0.96422 * f(fy + a / 500)
    val y50 = 1.0 * f(fy)
    val z50 = 0.82521 * f(fy - b / 200)
    // Bradford chromatic adaptation D50 -> D65.
    val x = 0.9555766 * x50 - 0.0230393 * y50 + 0.0631636 * z50
    val y = -0.0282895 * x50 + 1.0099416 * y50 + 0.0210077 * z50
    val z = 0.0122982 * x50 - 0.0204830 * y50 + 1.3299098 * z50
    fun gamma(c: Double) = (if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055).coerceIn(0.0, 1.0).toFloat()
    return Color(
        gamma(3.2404542 * x - 1.5371385 * y - 0.4985314 * z),
        gamma(-0.9692660 * x + 1.8760108 * y + 0.0415560 * z),
        gamma(0.0556434 * x - 0.2040259 * y + 1.0572252 * z),
    )
}
