package com.andene.fern.canvas

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Mouth shapes for lip-sync (roadmap item 88): the Preston Blair set animators chart on an
 * X-sheet, named by the sounds that make them. Labels are those sound letters, the same in
 * every language.
 */
enum class MouthShape(val label: String) {
    REST("—"),
    MBP("MBP"),
    E("E"),
    AI("AI"),
    O("O"),
    U("U"),
    FV("FV"),
    L("L"),
    WQ("WQ"),
    ETC("etc"),
}

/**
 * Lip-sync from a soundtrack by loudness: each frame's mouth is chosen from how loud the audio
 * is while that frame is on screen - closed for silence, then wider for louder sound - the
 * quick automatic pass animators start from before refining shapes by ear. It reads volume
 * only, not speech (no recognition, nothing leaves the device).
 */
object LipSync {
    /**
     * Mouth shapes for per-frame loudness [levels] (RMS, 0..1). Levels are measured against the
     * loudest frame, so quiet and loud recordings chart alike; a frame below [SILENCE] of it is
     * at rest. Thresholds follow the usual openness order: rest, MBP (lips meeting), E, AI, O.
     */
    fun mouthsFor(levels: FloatArray): Map<Int, MouthShape> {
        val peak = levels.maxOrNull()?.takeIf { it > 0f } ?: return levels.indices.associateWith { MouthShape.REST }
        return levels.indices.associateWith { frame ->
            val relative = levels[frame] / peak
            when {
                relative < SILENCE -> MouthShape.REST
                relative < 0.3f -> MouthShape.MBP
                relative < 0.5f -> MouthShape.E
                relative < 0.75f -> MouthShape.AI
                else -> MouthShape.O
            }
        }
    }

    private const val SILENCE = 0.1f

    /**
     * Loudness (RMS of 16-bit PCM, 0..1) of [file] during each frame, frame f covering
     * [frameStartsMillis][f] to [frameStartsMillis][f + 1] (the list has one more entry than there
     * are frames). Decoded with the platform's MediaExtractor and MediaCodec, so any format the
     * device plays works; frames past the audio's end are silent. Needs a device's codecs.
     */
    fun levels(file: File, frameStartsMillis: List<Long>): FloatArray {
        val frames = frameStartsMillis.size - 1
        val sums = DoubleArray(frames)
        val counts = LongArray(frames)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return FloatArray(frames)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var samplesSeen = 0L
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val input = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (input >= 0) {
                            val size = extractor.readSampleData(codec.getInputBuffer(input)!!, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val output = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (output >= 0) {
                        val pcm = codec.getOutputBuffer(output)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        // Samples are interleaved by channel; each sample frame is one instant.
                        while (pcm.remaining() >= channels) {
                            var sample = 0.0
                            repeat(channels) { sample += pcm.get() / 32768.0 }
                            sample /= channels
                            val millis = samplesSeen * 1000 / sampleRate
                            val frame = frameAt(frameStartsMillis, millis)
                            if (frame in 0 until frames) {
                                sums[frame] += sample * sample
                                counts[frame]++
                            }
                            samplesSeen++
                        }
                        codec.releaseOutputBuffer(output, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
        return FloatArray(frames) { if (counts[it] == 0L) 0f else sqrt(sums[it] / counts[it]).toFloat() }
    }

    /** The frame showing at [millis], by binary search over the frame start times. */
    internal fun frameAt(starts: List<Long>, millis: Long): Int {
        var low = 0
        var high = starts.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (starts[mid] <= millis) low = mid else high = mid - 1
        }
        return low
    }

    private const val TIMEOUT_US = 10_000L
}
