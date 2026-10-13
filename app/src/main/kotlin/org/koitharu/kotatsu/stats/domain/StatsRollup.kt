package org.koitharu.kotatsu.stats.domain

import org.koitharu.kotatsu.stats.data.StatsEntity
import java.util.Calendar
import java.util.TimeZone

/** Pure local-day reading totals. Used as a foundation for day/week/month charts. */
internal object StatsRollup {
    data class Day(val year: Int, val month: Int, val day: Int)

    fun byDay(records: Collection<StatsEntity>, timezone: TimeZone): Map<Day, Long> {
        val calendar = Calendar.getInstance(timezone)
        val grouped = linkedMapOf<Day, Long>()
        records.forEach { entry ->
            calendar.timeInMillis = entry.startedAt
            val key = Day(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH))
            grouped[key] = (grouped[key] ?: 0L) + entry.duration.coerceAtLeast(0L)
        }
        return grouped.toSortedMap(compareBy<Day> { it.year }.thenBy { it.month }.thenBy { it.day })
    }
}
