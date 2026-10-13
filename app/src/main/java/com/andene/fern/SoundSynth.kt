package com.andene.fern

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Which paper sound a pen makes (design roadmap D59). */
enum class PaperSound {
    /** Graphite on paper: bright, grainy noise. */
    PENCIL,

    /** A felt tip: softer, darker noise. */
    MARKER,
}

/**
 * The sound cues' synthesis (design roadmap D59), with no audio files: filtered noise for the
 * paper, its loudness following how fast the pen moves, and a two-note chime for confirmations.
 * Pure arithmetic on float samples in -1..1 at [sampleRate], so it is tested without a device;
 * [FernSounds] plays what it renders.
 */
class SoundSynth(private val sampleRate: Int, seed: Long = 1L) {
    private val random = java.util.Random(seed)

    /** The paper sound being drawn, or null between strokes. */
    var paper: PaperSound? = null

    /** How loud the paper should be, 0..1; the rendered level glides toward it. */
    var paperTarget = 0f

    private var paperLevel = 0f
    private var lowPass = 0f
    private var highPassIn = 0f
    private var highPassOut = 0f
    private var grain = 0f

    // The chime: elapsed samples, or -1 when silent.
    private var chimeAt = -1

    /** Starts the confirmation chime from its beginning. */
    fun chime() {
        chimeAt = 0
    }

    /** Whether anything is still sounding; the player stops its stream when not. */
    val sounding: Boolean get() = chimeAt >= 0 || paperLevel > SILENT || (paper != null && paperTarget > 0f)

    /** Fills [out] with the next samples. */
    fun render(out: FloatArray) {
        // About 20 ms to glide to a new loudness: smooth, without lagging the pen.
        val glide = 1f - exp(-1f / (GLIDE_SECONDS * sampleRate))
        val target = if (paper == null) 0f else paperTarget.coerceIn(0f, 1f)
        val pencil = paper != PaperSound.MARKER
        // One-pole filters: the pencil keeps the highs (paper tooth), the marker the lows (felt).
        val cutoff = if (pencil) PENCIL_CUTOFF_HZ else MARKER_CUTOFF_HZ
        val lowPassK = 1f - exp(-2f * PI.toFloat() * cutoff / sampleRate)
        val highPassK = exp(-2f * PI.toFloat() * HIGH_PASS_HZ / sampleRate)
        for (i in out.indices) {
            paperLevel += (target - paperLevel) * glide
            var sample = 0f
            if (paperLevel > SILENT) {
                val noise = random.nextFloat() * 2f - 1f
                lowPass += (noise - lowPass) * lowPassK
                highPassOut = highPassK * (highPassOut + lowPass - highPassIn)
                highPassIn = lowPass
                // Graphite catches on the paper's tooth: an irregular flutter in loudness.
                if (pencil && random.nextFloat() < GRAIN_CHANCE) grain = random.nextFloat()
                grain *= GRAIN_DECAY
                val texture = if (pencil) 1f + GRAIN_DEPTH * grain else 1f
                sample = highPassOut * paperLevel * texture * PAPER_GAIN
            }
            if (chimeAt >= 0) {
                sample += chimeSample(chimeAt)
                chimeAt = if (chimeAt + 1 >= CHIME_SECONDS * sampleRate) -1 else chimeAt + 1
            }
            out[i] = sample.coerceIn(-1f, 1f)
        }
    }

    // Two soft notes a fifth apart (A5 then E6), each fading fast, the second starting 70 ms in.
    private fun chimeSample(at: Int): Float {
        val t = at.toFloat() / sampleRate
        fun note(frequency: Float, start: Float): Float {
            if (t < start) return 0f
            val local = t - start
            return sin(2f * PI.toFloat() * frequency * local) * exp(-local / CHIME_DECAY_SECONDS)
        }
        return (note(880f, 0f) + note(1318.5f, 0.07f)) * CHIME_GAIN
    }

    private companion object {
        const val GLIDE_SECONDS = 0.02f
        const val SILENT = 1e-4f
        const val PENCIL_CUTOFF_HZ = 6000f
        const val MARKER_CUTOFF_HZ = 1200f
        const val HIGH_PASS_HZ = 300f
        const val GRAIN_CHANCE = 0.002f
        const val GRAIN_DECAY = 0.995f
        const val GRAIN_DEPTH = 1.5f
        const val PAPER_GAIN = 0.35f
        const val CHIME_SECONDS = 0.4f
        const val CHIME_DECAY_SECONDS = 0.06f
        const val CHIME_GAIN = 0.15f
    }
}
