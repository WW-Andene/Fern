package com.andene.fern.canvas

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Encodes frames into an H.264 MP4 with the platform's own encoder (MediaCodec + MediaMuxer, no
 * external service) - for the time-lapse replay (roadmap item 60) and animation export (80).
 *
 * Frames are fed as YUV 4:2:0 through the codec's input images, each with an explicit
 * presentation time, so a held frame is simply shown longer rather than repeated. Sizes are
 * padded up to a multiple of 16 (what H.264 encoders reliably accept) with [padColor].
 *
 * Only real hardware/software codecs can run this; it isn't exercised by the JVM tests (the
 * pixel conversion it relies on, [argbToYuv420], is). Not yet verified on a device.
 */
object Mp4Encoder {
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val TIMEOUT_US = 10_000L

    /**
     * Writes [frames] (ARGB pixels, [width] x [height], each shown for its [durationsMicros]
     * entry) to [out]. Pixels are produced one frame at a time, so a long video never holds
     * every frame in memory.
     */
    fun encode(frames: List<() -> IntArray>, durationsMicros: List<Long>, width: Int, height: Int, out: File, padColor: Int = 0xFFFFFFFF.toInt()) {
        require(frames.isNotEmpty() && frames.size == durationsMicros.size)
        val codedWidth = (width + 15) / 16 * 16
        val codedHeight = (height + 15) / 16 * 16
        val format = MediaFormat.createVideoFormat(MIME, codedWidth, codedHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, (codedWidth * codedHeight * 8).coerceAtLeast(1_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        val info = MediaCodec.BufferInfo()

        fun drain(endOfStream: Boolean) {
            while (true) {
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    index >= 0 -> {
                        val buffer = codec.getOutputBuffer(index)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0 && muxing) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            var timeUs = 0L
            val padded = IntArray(codedWidth * codedHeight)
            for (i in frames.indices) {
                val pixels = frames[i]()
                padded.fill(padColor)
                for (y in 0 until height) System.arraycopy(pixels, y * width, padded, y * codedWidth, width)
                var input: Int
                while (true) {
                    input = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (input >= 0) break
                    drain(endOfStream = false)
                }
                val image = codec.getInputImage(input)!!
                val y = image.planes[0]
                val u = image.planes[1]
                val v = image.planes[2]
                argbToYuv420(padded, codedWidth, codedHeight) { plane, px, py, value ->
                    val p = when (plane) { 0 -> y; 1 -> u; else -> v }
                    p.buffer.put(py * p.rowStride + px * p.pixelStride, value)
                }
                codec.queueInputBuffer(input, 0, codec.getInputBuffer(input)!!.capacity(), timeUs, 0)
                timeUs += durationsMicros[i]
                drain(endOfStream = false)
            }
            var input: Int
            while (true) {
                input = codec.dequeueInputBuffer(TIMEOUT_US)
                if (input >= 0) break
                drain(endOfStream = false)
            }
            codec.queueInputBuffer(input, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxing) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}

/**
 * Converts ARGB pixels to BT.601 limited-range YUV 4:2:0 (what H.264 encoders expect), handing
 * each sample to [put] as (plane 0 = Y / 1 = U / 2 = V, x, y, value). Chroma is the average of
 * each 2 x 2 block; [width] and [height] must be even. Alpha is ignored (composite first).
 */
fun argbToYuv420(argb: IntArray, width: Int, height: Int, put: (plane: Int, x: Int, y: Int, value: Byte) -> Unit) {
    for (y in 0 until height) for (x in 0 until width) {
        val c = argb[y * width + x]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        put(0, x, y, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte())
    }
    for (y in 0 until height / 2) for (x in 0 until width / 2) {
        var r = 0
        var g = 0
        var b = 0
        for (dy in 0..1) for (dx in 0..1) {
            val c = argb[(2 * y + dy) * width + 2 * x + dx]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        r /= 4; g /= 4; b /= 4
        put(1, x, y, (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte())
        put(2, x, y, (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte())
    }
}
