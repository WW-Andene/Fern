package com.andene.fern.canvas

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * A CMYK ICC profile (roadmap item 102) - the description of a printing press or paper a print
 * shop supplies (FOGRA39, SWOP, GRACoL...) - read well enough to convert colours with it:
 * [toCmyk] through the profile's B2A0 table (perceptual, from the profile connection space to
 * the press's inks) and [toRgb] through A2B0 back, for showing a CMYK value on screen. Reads ICC
 * v2 `mft1`/`mft2` and v4 `mAB `/`mBA ` tables with `curv` and `para` curves and multilinear
 * interpolation through the colour lookup table, a Lab or XYZ connection space, after the
 * ICC.1:2010 specification. Rendering intents other than perceptual and the profile's
 * black-point handling aren't applied.
 */
class IccProfile private constructor(
    private val pcsIsLab: Boolean,
    private val toPcs: IccTransform,
    private val fromPcs: IccTransform,
    /** The profile's description, when it has one, else empty. */
    val description: String,
) {
    /** [rgb] (sRGB, 0..1) as the ink amounts (C, M, Y, K, 0..1) the profile's press would print it with. */
    fun toCmyk(rgb: FloatArray): FloatArray = fromPcs.apply(encodePcs(srgbToXyzD50(rgb)), pcsIsLab).map { it.coerceIn(0f, 1f) }.toFloatArray()

    /** Ink amounts [cmyk] (0..1) as the sRGB colour (0..1) the press would print, for the screen. */
    fun toRgb(cmyk: FloatArray): FloatArray = xyzD50ToSrgb(decodePcs(toPcs.apply(cmyk, pcsIsLab)))

    private fun encodePcs(xyz: DoubleArray): FloatArray = if (pcsIsLab) {
        val lab = xyzToLab(xyz)
        floatArrayOf((lab[0] / 100).toFloat(), ((lab[1] + 128) / 255).toFloat(), ((lab[2] + 128) / 255).toFloat())
    } else {
        // XYZ in the 0..1.99997 range the PCS allows, normalised.
        FloatArray(3) { (xyz[it] / XYZ_PCS_MAX).toFloat() }
    }

    private fun decodePcs(v: FloatArray): DoubleArray = if (pcsIsLab) {
        labToXyz(doubleArrayOf(v[0] * 100.0, v[1] * 255.0 - 128, v[2] * 255.0 - 128))
    } else {
        DoubleArray(3) { v[it] * XYZ_PCS_MAX }
    }

    companion object {
        private const val XYZ_PCS_MAX = 1.0 + 32767.0 / 32768.0

        /** Reads a CMYK output profile; throws [IOException] for anything else or a damaged file. */
        fun read(bytes: ByteArray): IccProfile {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            if (bytes.size < 132 || String(bytes, 36, 4, Charsets.US_ASCII) != "acsp") throw IOException("Not an ICC profile")
            val space = String(bytes, 16, 4, Charsets.US_ASCII)
            if (space != "CMYK") throw IOException("Not a CMYK profile (it's $space)")
            val pcs = String(bytes, 20, 4, Charsets.US_ASCII)
            if (pcs != "Lab " && pcs != "XYZ ") throw IOException("Unknown connection space $pcs")
            val count = buf.getInt(128)
            val tags = HashMap<String, Pair<Int, Int>>()
            for (i in 0 until count) {
                val at = 132 + i * 12
                tags[String(bytes, at, 4, Charsets.US_ASCII)] = buf.getInt(at + 4) to buf.getInt(at + 8)
            }
            fun table(name: String): IccTransform {
                val (offset, size) = tags[name] ?: throw IOException("The profile has no $name table")
                return IccTransform.parse(bytes, offset, size)
            }
            val description = tags["desc"]?.let { (offset, size) -> readDescription(bytes, offset, size) }.orEmpty()
            return IccProfile(pcs == "Lab ", table("A2B0"), table("B2A0"), description)
        }

        private fun readDescription(bytes: ByteArray, offset: Int, size: Int): String = runCatching {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            when (String(bytes, offset, 4, Charsets.US_ASCII)) {
                "desc" -> String(bytes, offset + 12, (buf.getInt(offset + 8) - 1).coerceIn(0, size - 12), Charsets.US_ASCII)
                "mluc" -> {
                    val length = buf.getInt(offset + 20)
                    val start = buf.getInt(offset + 24)
                    String(bytes, offset + start, length, Charsets.UTF_16BE)
                }
                else -> ""
            }
        }.getOrDefault("").trim()
    }
}

/** sRGB (0..1) to XYZ relative to the D50 white the ICC connection space uses (sRGB's own matrix, Bradford-adapted). */
fun srgbToXyzD50(rgb: FloatArray): DoubleArray {
    val r = srgbToLinear(rgb[0]).toDouble(); val g = srgbToLinear(rgb[1]).toDouble(); val b = srgbToLinear(rgb[2]).toDouble()
    return doubleArrayOf(
        0.4360747 * r + 0.3850649 * g + 0.1430804 * b,
        0.2225045 * r + 0.7168786 * g + 0.0606169 * b,
        0.0139322 * r + 0.0971045 * g + 0.7141733 * b,
    )
}

fun xyzD50ToSrgb(xyz: DoubleArray): FloatArray {
    val (x, y, z) = Triple(xyz[0], xyz[1], xyz[2])
    val r = 3.1338561 * x - 1.6168667 * y - 0.4906146 * z
    val g = -0.9787684 * x + 1.9161415 * y + 0.0334540 * z
    val b = 0.0719453 * x - 0.2289914 * y + 1.4052427 * z
    return floatArrayOf(linearToSrgb(r.toFloat().coerceIn(0f, 1f)), linearToSrgb(g.toFloat().coerceIn(0f, 1f)), linearToSrgb(b.toFloat().coerceIn(0f, 1f)))
}

private val D50 = doubleArrayOf(0.9642, 1.0, 0.8249)

fun xyzToLab(xyz: DoubleArray): DoubleArray {
    fun f(t: Double) = if (t > 216.0 / 24389) t.pow(1.0 / 3) else (24389.0 / 27 * t + 16) / 116
    val fx = f(xyz[0] / D50[0]); val fy = f(xyz[1] / D50[1]); val fz = f(xyz[2] / D50[2])
    return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

fun labToXyz(lab: DoubleArray): DoubleArray {
    val fy = (lab[0] + 16) / 116
    val fx = fy + lab[1] / 500
    val fz = fy - lab[2] / 200
    fun inv(t: Double) = if (t * t * t > 216.0 / 24389) t * t * t else (116 * t - 16) * 27 / 24389
    return doubleArrayOf(inv(fx) * D50[0], inv(fy) * D50[1], inv(fz) * D50[2])
}

/**
 * One ICC lookup transform: curves, an optional matrix, a colour lookup table (CLUT) and more
 * curves, in the order its type runs them. Values are 0..1 on both sides; [apply] gets told
 * whether the connection space is Lab so the legacy 16-bit Lab encoding of `mft2` can be
 * matched (L 0..100 as 0..0xFF00).
 */
internal class IccTransform(
    private val inputs: Int,
    private val outputs: Int,
    private val legacyLab16: Boolean,
    private val stages: List<(FloatArray) -> FloatArray>,
    private val pcsIsInput: Boolean,
) {
    fun apply(values: FloatArray, pcsIsLab: Boolean): FloatArray {
        var v = values.copyOf(inputs)
        // mft2 holds Lab at 0..0xFF00 for 0..100 (and a/b alike), not 0..0xFFFF.
        val legacy = legacyLab16 && pcsIsLab
        // Into the table: 0..1 (L 0..100) becomes 0..0xFF00/0xFFFF; out of it, back.
        if (legacy && pcsIsInput) v = FloatArray(v.size) { v[it] * 65280f / 65535f }
        for (stage in stages) v = stage(v)
        if (legacy && !pcsIsInput) v = FloatArray(v.size) { (v[it] * 65535f / 65280f).coerceIn(0f, 1f) }
        return v.copyOf(outputs)
    }

    companion object {
        fun parse(bytes: ByteArray, offset: Int, size: Int): IccTransform {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            if (offset < 0 || offset + size > bytes.size) throw IOException("A profile table lies outside the file")
            return when (val type = String(bytes, offset, 4, Charsets.US_ASCII)) {
                "mft1", "mft2" -> {
                    val wide = type == "mft2"
                    val i = bytes[offset + 8].toInt() and 0xFF
                    val o = bytes[offset + 9].toInt() and 0xFF
                    val grid = bytes[offset + 10].toInt() and 0xFF
                    val n = if (wide) buf.getShort(offset + 48).toInt() and 0xFFFF else 256
                    val m = if (wide) buf.getShort(offset + 50).toInt() and 0xFFFF else 256
                    var at = offset + if (wide) 52 else 48
                    val unit = if (wide) 2 else 1
                    fun value(p: Int) = if (wide) (buf.getShort(p).toInt() and 0xFFFF) / 65535f else (bytes[p].toInt() and 0xFF) / 255f
                    val inCurves = List(i) { c -> FloatArray(n) { value(at + (c * n + it) * unit) } }
                    at += i * n * unit
                    val points = pow(grid, i)
                    val clut = FloatArray(points * o) { value(at + it * unit) }
                    at += points * o * unit
                    val outCurves = List(o) { c -> FloatArray(m) { value(at + (c * m + it) * unit) } }
                    val gridPoints = IntArray(i) { grid }
                    IccTransform(
                        i, o, wide,
                        listOf({ v -> tableCurves(v, inCurves) }, { v -> lookup(v, gridPoints, clut, o) }, { v -> tableCurves(v, outCurves) }),
                        pcsIsInput = o == 4, // B2A: PCS in, CMYK out
                    )
                }
                "mAB ", "mBA " -> {
                    val aToB = type == "mAB "
                    val i = bytes[offset + 8].toInt() and 0xFF
                    val o = bytes[offset + 9].toInt() and 0xFF
                    fun off(k: Int) = buf.getInt(offset + 12 + k * 4).let { if (it == 0) -1 else offset + it }
                    val bCurves = off(0).takeIf { it >= 0 }?.let { readCurves(bytes, it, if (aToB) o else i) }
                    val matrix = off(1).takeIf { it >= 0 }?.let { p -> DoubleArray(12) { buf.getInt(p + it * 4) / 65536.0 } }
                    val mCurves = off(2).takeIf { it >= 0 }?.let { readCurves(bytes, it, 3) }
                    val clutAt = off(3)
                    val aCurves = off(4).takeIf { it >= 0 }?.let { readCurves(bytes, it, if (aToB) i else o) }
                    val clutIn = if (aToB) i else 3
                    val clutOut = if (aToB) 3 else o
                    val clut: ((FloatArray) -> FloatArray)? = if (clutAt >= 0) {
                        val grid = IntArray(clutIn) { bytes[clutAt + it].toInt() and 0xFF }
                        val precision = bytes[clutAt + 16].toInt() and 0xFF
                        val count = grid.fold(1) { a, g -> a * g } * clutOut
                        val data = FloatArray(count) { k ->
                            if (precision == 1) (bytes[clutAt + 20 + k].toInt() and 0xFF) / 255f
                            else (buf.getShort(clutAt + 20 + k * 2).toInt() and 0xFFFF) / 65535f
                        }
                        val stage: (FloatArray) -> FloatArray = { v -> lookup(v, grid, data, clutOut) }
                        stage
                    } else null
                    val matrixStage: ((FloatArray) -> FloatArray)? = matrix?.let { mx ->
                        { v: FloatArray -> FloatArray(3) { r -> (mx[r * 3] * v[0] + mx[r * 3 + 1] * v[1] + mx[r * 3 + 2] * v[2] + mx[9 + r]).toFloat().coerceIn(0f, 1f) } }
                    }
                    fun curveStage(c: List<(Float) -> Float>?): ((FloatArray) -> FloatArray)? = c?.let { list -> { v: FloatArray -> curves(v, list) } }
                    val stages: List<(FloatArray) -> FloatArray> = if (aToB) {
                        listOfNotNull(curveStage(aCurves), clut, curveStage(mCurves), matrixStage, curveStage(bCurves))
                    } else {
                        listOfNotNull(curveStage(bCurves), matrixStage, curveStage(mCurves), clut, curveStage(aCurves))
                    }
                    IccTransform(i, o, legacyLab16 = false, stages = stages, pcsIsInput = !aToB)
                }
                else -> throw IOException("Unsupported profile table type $type")
            }
        }

        private fun pow(base: Int, exp: Int): Int = (0 until exp).fold(1) { a, _ -> a * base }

        private fun tableCurves(v: FloatArray, tables: List<FloatArray>) = FloatArray(v.size) { c -> sampleTable(tables[c], v[c]) }

        private fun sampleTable(table: FloatArray, x: Float): Float {
            if (table.size == 1) return table[0]
            val pos = x.coerceIn(0f, 1f) * (table.size - 1)
            val k = pos.toInt().coerceAtMost(table.size - 2)
            val t = pos - k
            return table[k] * (1 - t) + table[k + 1] * t
        }

        /** Curves stored one after another (`curv` or `para`, each padded to four bytes). */
        private fun readCurves(bytes: ByteArray, start: Int, count: Int): List<(Float) -> Float> {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            var at = start
            return List(count) {
                val type = String(bytes, at, 4, Charsets.US_ASCII)
                val curve: (Float) -> Float
                val length: Int
                when (type) {
                    "curv" -> {
                        val n = buf.getInt(at + 8)
                        curve = when (n) {
                            0 -> { x -> x }
                            1 -> { val gamma = (buf.getShort(at + 12).toInt() and 0xFFFF) / 256f; { x -> x.coerceIn(0f, 1f).pow(gamma) } }
                            else -> { val table = FloatArray(n) { k -> (buf.getShort(at + 12 + k * 2).toInt() and 0xFFFF) / 65535f }; { x -> sampleTable(table, x) } }
                        }
                        length = 12 + n * 2
                    }
                    "para" -> {
                        val function = buf.getShort(at + 8).toInt()
                        val params = listOf(1, 3, 4, 5, 7)[function.coerceIn(0, 4)]
                        val p = DoubleArray(params) { buf.getInt(at + 12 + it * 4) / 65536.0 }
                        curve = { x -> parametric(function, p, x.toDouble()).toFloat().coerceIn(0f, 1f) }
                        length = 12 + params * 4
                    }
                    else -> throw IOException("Unsupported curve type $type")
                }
                at += (length + 3) / 4 * 4
                curve
            }
        }

        private fun curves(v: FloatArray, curves: List<(Float) -> Float>) = FloatArray(v.size) { c -> curves[c](v[c]) }

        /** The ICC parametric curve types 0-4. */
        private fun parametric(type: Int, p: DoubleArray, x: Double): Double = when (type) {
            0 -> x.pow(p[0])
            1 -> if (x >= -p[2] / p[1]) (p[1] * x + p[2]).pow(p[0]) else 0.0
            2 -> if (x >= -p[2] / p[1]) (p[1] * x + p[2]).pow(p[0]) + p[3] else p[3]
            3 -> if (x >= p[4]) (p[1] * x + p[2]).pow(p[0]) else p[3] * x
            else -> if (x >= p[4]) (p[1] * x + p[2]).pow(p[0]) + p[5] else p[3] * x + p[6]
        }

        /**
         * Multilinear interpolation through a CLUT of [grid] points per input (first input
         * varying slowest, as ICC stores it) with [outs] values per point.
         */
        fun lookup(v: FloatArray, grid: IntArray, clut: FloatArray, outs: Int): FloatArray {
            val dims = grid.size
            val base = IntArray(dims)
            val frac = FloatArray(dims)
            for (d in 0 until dims) {
                val pos = v[d].coerceIn(0f, 1f) * (grid[d] - 1)
                base[d] = pos.toInt().coerceAtMost(grid[d] - 2).coerceAtLeast(0)
                frac[d] = if (grid[d] > 1) pos - base[d] else 0f
            }
            // Strides: the last input varies fastest.
            val stride = IntArray(dims)
            var s = outs
            for (d in dims - 1 downTo 0) {
                stride[d] = s
                s *= grid[d]
            }
            val out = FloatArray(outs)
            for (corner in 0 until (1 shl dims)) {
                var weight = 1f
                var index = 0
                for (d in 0 until dims) {
                    val up = (corner shr d) and 1 == 1
                    if (up && grid[d] == 1) { weight = 0f; break }
                    weight *= if (up) frac[d] else 1 - frac[d]
                    index += (base[d] + if (up) 1 else 0) * stride[d]
                }
                if (weight == 0f) continue
                for (k in 0 until outs) out[k] += clut[index + k] * weight
            }
            return out
        }
    }
}
