package org.koitharu.kotatsu.stats.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingActivityTimeTest {
    @Test fun capsIdleGapsAtTwoMinutes() {
        assertEquals(120_000L, ReadingActivityTime.activeInterval(1_000L, 601_000L))
    }

    @Test fun preservesRealShortIntervals() {
        assertEquals(25_000L, ReadingActivityTime.activeInterval(1_000L, 26_000L))
    }

    @Test fun ignoresClockRollbackOrRepeatedEvent() {
        assertEquals(0L, ReadingActivityTime.activeInterval(20_000L, 10_000L))
        assertEquals(0L, ReadingActivityTime.activeInterval(20_000L, 20_000L))
    }
}
