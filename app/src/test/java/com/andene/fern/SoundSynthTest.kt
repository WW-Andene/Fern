package com.andene.fern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Design roadmap D59: the sound cues' synthesis - silent at rest, the paper following the pen, the chime ending. */
class SoundSynthTest {
    private val rate = 22050

    private fun SoundSynth.take(samples: Int) = FloatArray(samples).also { render(it) }

    private fun rms(samples: FloatArray) = kotlin.math.sqrt(samples.map { it * it }.average())

    // Sign changes per sample: a rough measure of how bright a noise is.
    private fun crossings(samples: FloatArray) = samples.toList().zipWithNext().count { (a, b) -> (a < 0f) != (b < 0f) }.toDouble() / samples.size

    @Test
    fun silentAtRest() {
        val synth = SoundSynth(rate)
        assertFalse(synth.sounding)
        assertTrue(synth.take(1024).all { it == 0f })
    }

    @Test
    fun paperFollowsThePenAndFadesWhenItLifts() {
        val synth = SoundSynth(rate)
        synth.paper = PaperSound.PENCIL
        synth.paperTarget = 0.2f
        val slow = rms(synth.take(rate / 4).copyOfRange(rate / 8, rate / 4))
        synth.paperTarget = 1f
        val fast = rms(synth.take(rate / 4).copyOfRange(rate / 8, rate / 4))
        assertTrue("slow $slow, fast $fast", fast > slow * 2 && slow > 0.0)
        synth.paper = null
        synth.take(rate / 2)
        assertFalse(synth.sounding)
    }

    @Test
    fun markerIsDarkerThanPencil() {
        fun brightness(paper: PaperSound) = SoundSynth(rate).run {
            this.paper = paper
            paperTarget = 1f
            crossings(take(rate / 2).copyOfRange(rate / 4, rate / 2))
        }
        assertTrue(brightness(PaperSound.MARKER) < brightness(PaperSound.PENCIL))
    }

    @Test
    fun chimeSoundsThenEnds() {
        val synth = SoundSynth(rate)
        synth.chime()
        assertTrue(synth.sounding)
        val chime = synth.take(rate / 2)
        assertTrue(chime.any { abs(it) > 0.05f })
        assertTrue(chime.all { it in -1f..1f })
        assertFalse(synth.sounding)
        assertEquals(0f, synth.take(16).maxOf { abs(it) }, 0f)
    }
}
