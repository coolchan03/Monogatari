package org.koitharu.kotatsu.stats.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeEstimatorTest {
    @Test fun titlePaceOverridesMediaAndGlobalPace() {
        assertEquals(15_000L, TimeEstimator.mangaRemainingMs(3, 5_000L, 7_000L, 9_000L))
    }

    @Test fun invalidPaceFallsBackToEightSecondsPerPage() {
        assertEquals(16_000L, TimeEstimator.mangaRemainingMs(2, 0L, -1L, null))
    }

    @Test fun novelsDefaultToTwoHundredThirtyWordsPerMinute() {
        assertEquals(60_000L, TimeEstimator.wordsRemainingMs(230, null))
    }

    @Test fun noNegativeRemainingMediaTime() {
        assertEquals(0L, TimeEstimator.mediaRemainingMs(10_000L, 12_000L))
    }
}
