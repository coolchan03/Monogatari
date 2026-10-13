package org.koitharu.kotatsu.stats.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.stats.data.StatsEntity
import java.time.Instant
import java.util.TimeZone

class StatsInsightsTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z").toEpochMilli()
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun groupsDailySessionsAndComputesCurrentStreak() {
        val records = listOf(
            StatsEntity(1L, now - 3_600_000L, 60 * 60_000L, 12),
            StatsEntity(1L, now - 86_400_000L, 20 * 60_000L, 4),
        )
        val summary = StatsInsights.compute(records, now, utc, goalMinutes = 30)
        assertEquals(60 * 60_000L, summary.todayMs)
        assertEquals(80 * 60_000L, summary.weekMs)
        assertEquals(1, summary.streakDays)
        assertEquals(1, summary.goalDaysIn30)
        assertEquals(80 * 60_000L, summary.totalMs)
    }

    @Test fun yesterdayCanAnchorStreakBeforeTodayReachesGoal() {
        val records = listOf(
            StatsEntity(1L, now - 86_400_000L, 45 * 60_000L, 8),
            StatsEntity(1L, now - 2 * 86_400_000L, 45 * 60_000L, 8),
        )
        val summary = StatsInsights.compute(records, now, utc, goalMinutes = 30)
        assertEquals(0L, summary.todayMs)
        assertEquals(2, summary.streakDays)
        assertEquals(2, summary.goalDaysIn30)
    }
}
