package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 132: time worked and drawing done, per document. */
class DocumentStatsTest {
    @Test
    fun activeTimeCountsGapsUpToAMinute() {
        val first = DocumentStats().after(StatsAction.DRAW, startedAt = 1_000, now = 3_000, previousEnd = null)
        assertEquals(2_000, first.activeMillis) // the stroke itself
        assertEquals(1_000, first.firstAction)
        val second = first.after(StatsAction.ERASE, startedAt = 10_000, now = 11_000, previousEnd = 3_000)
        assertEquals(10_000, second.activeMillis) // 8 s since the last action
        val afterBreak = second.after(StatsAction.DRAW, startedAt = 600_000, now = 601_000, previousEnd = 11_000)
        assertEquals(10_000 + ACTIVE_GAP_CAP_MILLIS, afterBreak.activeMillis) // a long break counts a minute
        assertEquals(2, afterBreak.strokesDrawn)
        assertEquals(1, afterBreak.erases)
        assertEquals(1_000, afterBreak.firstAction)
        assertEquals(601_000, afterBreak.lastAction)
    }

    @Test
    fun drawingAndErasingAreCountedAndReported() {
        val reported = ArrayList<DocumentStats>()
        val state = CanvasState(onStatsChanged = { reported.add(it) })
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(50.0, 0.0)); state.endStroke()
        state.beginErase(WorldPoint(25.0, 0.0)); state.endErase()
        assertEquals(1, state.stats.strokesDrawn)
        assertEquals(1, state.stats.erases)
        assertEquals(2, reported.size)
        assertTrue(state.stats.firstAction > 0)
        state.loadStats(DocumentStats(strokesDrawn = 40))
        assertEquals(40, state.stats.strokesDrawn)
        assertEquals("5 min", formatDuration(5 * 60_000L + 59_000))
        assertEquals("2 h 05 min", formatDuration((125 * 60_000).toLong()))
    }
}
