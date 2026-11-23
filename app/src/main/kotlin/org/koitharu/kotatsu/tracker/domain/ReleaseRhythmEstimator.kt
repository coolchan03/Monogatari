package org.koitharu.kotatsu.tracker.domain

/** Estimates the next chapter arrival from a title's recent upload dates. No calendar alarms. */
internal object ReleaseRhythmEstimator {
    private const val DAY_MS = 86_400_000L
    /** The median gap between recent releases, or null when there is too little history to trust one. */
    fun typicalGapMs(dates: List<Long>, now: Long): Long? {
        val sorted = dates.filter { it > 0L && it <= now }.distinct().sorted()
        if (sorted.size < 3) return null
        val gaps = sorted.zipWithNext { a, b -> b - a }
            .filter { it in DAY_MS..(90L * DAY_MS) }.takeLast(6).sorted()
        if (gaps.size < 2) return null
        return gaps[gaps.size / 2]
    }

    fun nextExpectedAt(dates: List<Long>, now: Long): Long? {
        val median = typicalGapMs(dates, now) ?: return null
        val last = dates.filter { it > 0L && it <= now }.max()
        val predicted = last + median
        return predicted.takeIf { it >= now - 3L * DAY_MS && it <= now + 30L * DAY_MS }
    }
    fun expectedWithinWeek(dates: List<Long>, now: Long): Boolean =
        nextExpectedAt(dates, now)?.let { it in now..(now + 7 * DAY_MS) } ?: false
}
