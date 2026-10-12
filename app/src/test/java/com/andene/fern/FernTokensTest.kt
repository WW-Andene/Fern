package com.andene.fern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Design roadmap D1: the tokens follow the CLAUDE.md §7 scale, and UI code uses them. */
class FernTokensTest {
    @Test
    fun roundingFollowsTheTieBreakRules() {
        // The worked examples of CLAUDE.md §7.1.
        assertEquals(8, scaleNearest(10f))
        assertEquals(12, scaleNearest(13f))
        assertEquals(16, scaleNearest(15f))
        assertEquals(12, scaleNearest(11f))
        assertEquals(16, scaleNearest(17f))
    }

    @Test
    fun noRawDimensionsOutsideTheTokens() {
        val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "FernTokens.kt" }
        val raw = Regex("""(?<![\w.])\d+(\.\d+)?f?\.(dp|sp)\b""")
        val offenders = sources.flatMap { file -> file.readLines().withIndex().filter { raw.containsMatchIn(it.value) }.map { "${file.name}:${it.index + 1}" } }.toList()
        assertTrue("Raw dp/sp literals; use FernScale, FernSpace, FernSize or FernType: $offenders", offenders.isEmpty())
    }
}
