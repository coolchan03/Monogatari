package org.koitharu.kotatsu.stats.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.stats.data.StatsEntity
import java.util.TimeZone

class StatsRollupTest {
    @Test fun groupsMultipleSessionsIntoLocalDays() {
        val sessions = listOf(
            StatsEntity(1, 0L, 3_000L, 2),
            StatsEntity(2, 3_600_000L, 7_000L, 4),
            StatsEntity(1, 86_400_000L, 5_000L, 2),
        )
        val buckets = StatsRollup.byDay(sessions, TimeZone.getTimeZone("UTC"))
        assertEquals(10_000L, buckets[StatsRollup.Day(1970, 1, 1)])
        assertEquals(5_000L, buckets[StatsRollup.Day(1970, 1, 2)])
    }

    @Test fun ignoresNegativeDurationAndRespectsTimezone() {
        val sessions = listOf(StatsEntity(1, 0L, -200L, 0),
            StatsEntity(2, 30_000L, 4_000L, 1))
        val buckets = StatsRollup.byDay(sessions, TimeZone.getTimeZone("GMT-05:00"))
        assertEquals(4_000L, buckets[StatsRollup.Day(1969, 12, 31)])
    }
}
