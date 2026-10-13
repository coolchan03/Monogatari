package org.koitharu.kotatsu.stats.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeEstimatorTest {
    @Test fun fallsBackToEightSecondsPerUnreadPage() {
        assertEquals(80_000L, TimeEstimator.mangaRemainingMs(10, null, null, null))
        assertEquals(0L, TimeEstimator.mangaRemainingMs(-3, null, null, null))
    }

    @Test fun prefersTitlePaceThenMediaAndGlobal() {
        assertEquals(30_000L, TimeEstimator.mangaRemainingMs(10, 3000L, 5000L, 9000L))
        assertEquals(50_000L, TimeEstimator.mangaRemainingMs(10, null, 5000L, 9000L))
        assertEquals(90_000L, TimeEstimator.mangaRemainingMs(10, -1L, null, 9000L))
    }

    @Test fun wordsAndMediaNeverGoNegative() {
        assertEquals(60_000L, TimeEstimator.wordsRemainingMs(230, null))
        assertEquals(0L, TimeEstimator.wordsRemainingMs(-100, null))
        assertEquals(1000L, TimeEstimator.mediaRemainingMs(8000L, 7000L))
        assertEquals(0L, TimeEstimator.mediaRemainingMs(8000L, 9000L))
    }

    @Test fun keepsPreviousPaceRegressionCases() {
        assertEquals(15_000L, TimeEstimator.mangaRemainingMs(3, 5_000L, 7_000L, 9_000L))
        assertEquals(16_000L, TimeEstimator.mangaRemainingMs(2, 0L, -1L, null))
        assertEquals(0L, TimeEstimator.mediaRemainingMs(10_000L, 12_000L))
    }
}
