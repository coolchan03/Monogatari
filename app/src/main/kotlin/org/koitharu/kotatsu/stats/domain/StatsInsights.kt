package org.koitharu.kotatsu.stats.domain

import org.koitharu.kotatsu.stats.data.StatsEntity
import java.util.Calendar
import java.util.TimeZone

/** Calendar-based statistics, independent of UI and daylight-saving boundaries. */
internal object StatsInsights {
    data class Summary(
        val todayMs: Long,
        val weekMs: Long,
        val monthMs: Long,
        val streakDays: Int,
        val goalDaysIn30: Int,
        val hourMs: List<Long>,
        val dailyMs: List<Long>, // oldest to newest, including today
        val totalMs: Long,
    )

    fun compute(records: Collection<StatsEntity>, nowMs: Long,
        zone: TimeZone, goalMinutes: Int): Summary {
        require(goalMinutes in 1..240)
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = nowMs
        val today = StatsRollup.Day(calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        // Ignore invalid/future sessions so imports cannot inflate goals.
        val valid = records.filter { it.startedAt in 1..nowMs && it.duration > 0L }
        val daily = StatsRollup.byDay(valid, zone)
        val goalMs = goalMinutes * 60_000L
        fun back(days: Int): StatsRollup.Day {
            val temp = Calendar.getInstance(zone)
            temp.timeInMillis = nowMs
            temp.add(Calendar.DAY_OF_YEAR, -days)
            return StatsRollup.Day(temp.get(Calendar.YEAR), temp.get(Calendar.MONTH) + 1,
                temp.get(Calendar.DAY_OF_MONTH))
        }
        val week = (0..6).sumOf { daily[back(it)].orZero() }
        val currentMonthTotal = daily.entries.sumOf { (day, duration) ->
            if (day.year == currentYear && day.month == currentMonth + 1) duration else 0L
        }
        var streak = 0
        // A streak can be active yesterday when today has not yet met the goal.
        var offset = if (daily[today].orZero() >= goalMs) 0 else 1
        while (offset <= 366 && daily[back(offset)].orZero() >= goalMs) {
            streak++
            offset++
        }
        val goalDays = (0..29).count { daily[back(it)].orZero() >= goalMs }
        val hours = LongArray(24)
        valid.forEach { record ->
            if (record.startedAt in 0..nowMs) {
                calendar.timeInMillis = record.startedAt
                hours[calendar.get(Calendar.HOUR_OF_DAY)] += record.duration.coerceAtLeast(0L)
            }
        }
        return Summary(
            todayMs = daily[today].orZero(),
            weekMs = week,
            monthMs = currentMonthTotal,
            streakDays = streak,
            goalDaysIn30 = goalDays,
            hourMs = hours.toList(),
            dailyMs = (29 downTo 0).map { daily[back(it)].orZero() },
            totalMs = valid.sumOf { it.duration },
        )
    }

    private fun Long?.orZero(): Long = this ?: 0L
}
