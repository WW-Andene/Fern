package com.andene.fern.canvas

/**
 * Usage statistics for one document (roadmap item 132, as Clip Studio keeps them): how long it
 * has been worked on and how much drawing went into it. Counted on this device only, from when
 * this was added; nothing leaves the app.
 *
 * [activeMillis] is time spent working: each action adds the time since the previous one, but
 * a gap longer than [ACTIVE_GAP_CAP_MILLIS] (the user stepped away, or the document sat open)
 * counts only that much, so idle time doesn't inflate it.
 */
data class DocumentStats(
    val activeMillis: Long = 0L,
    val strokesDrawn: Long = 0L,
    val erases: Long = 0L,
    /** When the first counted action happened (epoch millis), 0 before any. */
    val firstAction: Long = 0L,
    val lastAction: Long = 0L,
)

const val ACTIVE_GAP_CAP_MILLIS = 60_000L

/** What a drawing action counts as in [DocumentStats]. */
enum class StatsAction { DRAW, ERASE }

/**
 * These statistics after an [action] that began at [startedAt] and ended at [now]; the previous
 * action ended at [previousEnd] (null when this is the first since the document was opened).
 */
fun DocumentStats.after(action: StatsAction, startedAt: Long, now: Long, previousEnd: Long?): DocumentStats {
    val since = (now - (previousEnd ?: startedAt)).coerceAtLeast(0L)
    return copy(
        activeMillis = activeMillis + minOf(since, ACTIVE_GAP_CAP_MILLIS),
        strokesDrawn = strokesDrawn + if (action == StatsAction.DRAW) 1 else 0,
        erases = erases + if (action == StatsAction.ERASE) 1 else 0,
        firstAction = if (firstAction == 0L) startedAt else firstAction,
        lastAction = now,
    )
}
