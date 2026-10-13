package com.andene.fern

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Optional sound cues (design roadmap D59), off by default: the paper under the pen while
 * drawing ([stroke], [strokeEnd]) and a soft chime when an action is confirmed ([confirm]). The
 * sounds are synthesised ([SoundSynth]), so there are no audio files. [enabled] follows the
 * Settings switch.
 *
 * One stream plays everything, from a background thread that only runs while something sounds:
 * it starts on the first cue and stops itself once the last one has faded, so silence costs
 * nothing. Cues use the media volume, as the "sonification" content type, so they follow the
 * user's volume and silent settings like other app sounds.
 */
object FernSounds {
    var enabled by mutableStateOf(false)

    private const val SAMPLE_RATE = 22050
    private const val BLOCK = 512 // about 23 ms per write: short enough that a pen's sound follows it

    // The pen's speed, in px per ms, at which the paper is at full loudness.
    private const val FULL_SPEED = 2f

    private val lock = Object()
    private val synth = SoundSynth(SAMPLE_RATE, System.nanoTime())
    private var thread: Thread? = null

    /** The pen moved at [speed] (px per ms) with [paper]'s sound. Call on every move of a stroke. */
    fun stroke(paper: PaperSound, speed: Float) {
        if (!enabled) return
        synchronized(lock) {
            synth.paper = paper
            synth.paperTarget = (speed / FULL_SPEED).coerceIn(0f, 1f)
        }
        play()
    }

    /** The pen lifted: the paper sound fades out. */
    fun strokeEnd() {
        synchronized(lock) {
            synth.paper = null
            synth.paperTarget = 0f
        }
    }

    /** Something the user did was confirmed (deleted with Undo offered, copied, exported). */
    fun confirm() {
        if (!enabled) return
        synchronized(lock) { synth.chime() }
        play()
    }

    private fun play() {
        synchronized(lock) {
            if (thread?.isAlive == true) return
            thread = Thread(::run, "FernSounds").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun run() {
        val bufferBytes = maxOf(
            AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
            BLOCK * Float.SIZE_BYTES * 2,
        )
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return // no audio output: the cues are optional, drawing goes on silently
        val block = FloatArray(BLOCK)
        try {
            track.play()
            while (true) {
                synchronized(lock) {
                    if (!synth.sounding) {
                        thread = null
                        return
                    }
                    synth.render(block)
                }
                // Blocking write: paces the loop to the audio clock.
                track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
