package org.koitharu.kotatsu.tracker.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseRhythmEstimatorTest {
    private val day = 86_400_000L
    @Test fun predictsWeeklyUpdatesFromPriorChapters() {
        val dates = listOf(1L, 8L, 15L).map { it * day }
        assertTrue(ReleaseRhythmEstimator.expectedWithinWeek(dates, 20L * day))
        assertFalse(ReleaseRhythmEstimator.expectedWithinWeek(dates, 25L * day))
    }
    @Test fun refusesToGuessFromSparseMetadata() {
        assertNull(ReleaseRhythmEstimator.nextExpectedAt(listOf(7L * day), 8L * day))
    }
}
