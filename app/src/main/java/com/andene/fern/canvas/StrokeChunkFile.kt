package com.andene.fern.canvas

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A document's strokes file split into chunks (roadmap item 53), so the part of a big document
 * in view can be read and shown before the rest: opening a million-stroke document takes ~4 s
 * to parse in full.
 *
 * Chunks are runs of [CHUNK_STROKES] consecutive strokes - drawing order is stacking order, so
 * strokes are never regrouped by place - each a [StrokeCodec] block. Strokes drawn around the
 * same time usually sit near each other, so a chunk's bounds are usually tight. Layout:
 * ```
 * int magic 'FCHK'  int version  int chunk count
 * per chunk: int byte length  double minX  double minY  double maxX  double maxY (padded by width)
 * then each chunk's StrokeCodec block, in order
 * ```
 * The table of contents comes first so chunks out of view can be skipped without parsing.
 * A file that is a single StrokeCodec block (written before chunks existed) is read whole.
 */
object StrokeChunkFile {
    private const val MAGIC = 0x4643484B // "FCHK"
    private const val VERSION = 1

    /** Strokes per chunk: large enough that headers cost nothing, small enough to skip finely. */
    const val CHUNK_STROKES = 4096

    fun write(out: DataOutputStream, strokes: List<Stroke>) {
        val chunks = strokes.chunked(CHUNK_STROKES)
        val bodies = chunks.map { chunk ->
            ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), chunk) }.toByteArray()
        }
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(chunks.size)
        for (i in chunks.indices) {
            out.writeInt(bodies[i].size)
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (stroke in chunks[i]) {
                if (stroke.points.isEmpty()) continue
                minX = minOf(minX, stroke.minX - stroke.widthWorld)
                minY = minOf(minY, stroke.minY - stroke.widthWorld)
                maxX = maxOf(maxX, stroke.maxX + stroke.widthWorld)
                maxY = maxOf(maxY, stroke.maxY + stroke.widthWorld)
            }
            out.writeDouble(minX)
            out.writeDouble(minY)
            out.writeDouble(maxX)
            out.writeDouble(maxY)
        }
        for (body in bodies) out.write(body)
    }

    /** A world rectangle; chunks whose bounds intersect it are read first. */
    data class Area(val left: Double, val top: Double, val right: Double, val bottom: Double)

    /**
     * Reads every stroke, in order. With [first] and [onFirst], the chunks intersecting [first]
     * are parsed in a first pass (the others skipped unread) and handed to [onFirst], in order,
     * from the reading thread; a second pass over [reopen]'s stream then reads the rest. The
     * full list is what's returned. Without them, one streaming pass.
     */
    fun read(
        input: DataInputStream,
        first: Area? = null,
        onFirst: ((List<Stroke>) -> Unit)? = null,
        reopen: (() -> DataInputStream)? = null,
    ): List<Stroke> {
        input.mark(4)
        val magic = input.readInt()
        input.reset()
        if (magic != MAGIC) return StrokeCodec.read(input) // a single-block file from before chunks
        val toc = readToc(input)
        val count = toc.lengths.size
        val visible = BooleanArray(count) { i ->
            val b = toc.bounds[i]
            first != null && !(b[2] < first.left || b[0] > first.right || b[3] < first.top || b[1] > first.bottom)
        }
        val split = onFirst != null && reopen != null && visible.any { it } && !visible.all { it }
        if (!split) {
            val all = ArrayList<Stroke>()
            for (i in 0 until count) all.addAll(readChunk(input, toc.lengths[i]))
            if (onFirst != null && visible.any { it }) onFirst(all)
            return all
        }
        val parsed = arrayOfNulls<List<Stroke>>(count)
        for (i in 0 until count) {
            if (visible[i]) parsed[i] = readChunk(input, toc.lengths[i]) else skip(input, toc.lengths[i])
        }
        onFirst!!((0 until count).filter { visible[it] }.flatMap { parsed[it]!! })
        reopen!!().use { second ->
            readToc(second)
            for (i in 0 until count) {
                if (visible[i]) skip(second, toc.lengths[i]) else parsed[i] = readChunk(second, toc.lengths[i])
            }
        }
        val all = ArrayList<Stroke>(parsed.sumOf { it!!.size })
        for (chunk in parsed) all.addAll(chunk!!)
        return all
    }

    private class Toc(val lengths: IntArray, val bounds: Array<DoubleArray>)

    private fun readToc(input: DataInputStream): Toc {
        if (input.readInt() != MAGIC) throw IOException("Not a chunked stroke file")
        val version = input.readInt()
        if (version > VERSION) throw IOException("Chunk format $version is newer than supported $VERSION")
        val count = input.readInt()
        if (count < 0 || count > MAX_CHUNKS) throw IOException("Bad chunk count $count")
        val lengths = IntArray(count)
        val bounds = Array(count) { DoubleArray(4) }
        for (i in 0 until count) {
            lengths[i] = input.readInt()
            if (lengths[i] < 0) throw IOException("Bad chunk length ${lengths[i]}")
            for (k in 0 until 4) bounds[i][k] = input.readDouble()
        }
        return Toc(lengths, bounds)
    }

    private fun readChunk(input: DataInputStream, length: Int): List<Stroke> =
        parse(ByteArray(length).also { input.readFully(it) })

    private fun skip(input: DataInputStream, length: Int) {
        var left = length.toLong()
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) {
                if (input.read() == -1) throw IOException("Chunk past the end of the file")
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun parse(bytes: ByteArray): List<Stroke> = StrokeCodec.read(DataInputStream(bytes.inputStream()))

    // Far beyond any real document (64M strokes); guards against a corrupt count.
    private const val MAX_CHUNKS = 16384
}
