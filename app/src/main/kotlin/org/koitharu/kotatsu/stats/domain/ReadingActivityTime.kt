package org.koitharu.kotatsu.stats.domain

/** Counts reading-event intervals, excluding time spent idle between events. */
internal object ReadingActivityTime {
    private const val MAX_EVENT_GAP_MS = 120_000L

    fun activeInterval(lastEventAt: Long, eventAt: Long): Long {
        if (eventAt <= lastEventAt) return 0L
        return (eventAt - lastEventAt).coerceAtMost(MAX_EVENT_GAP_MS)
    }
}
