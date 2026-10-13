package org.koitharu.kotatsu.stats.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.media.MediaType

class BacklogEstimatorTest {
    @Test fun selectsReadBranchAndRemainingFractionOnly() {
        val chapters = listOf(
            BacklogEstimator.Chapter(1, MediaType.MANGA, 1, "primary", 0,
                pages = 10, currentChapterId = 2, titleProgress = 0.75f),
            BacklogEstimator.Chapter(1, MediaType.MANGA, 2, "primary", 1,
                pages = 20, currentChapterId = 2, titleProgress = 0.75f),
            BacklogEstimator.Chapter(1, MediaType.MANGA, 3, "translation", 0,
                pages = 100, currentChapterId = 2, titleProgress = 0.75f),
            BacklogEstimator.Chapter(2, MediaType.BOOK, 4, null, 0, words = 230),
            BacklogEstimator.Chapter(3, MediaType.VIDEO, 5, null, 0),
        )
        val result = BacklogEstimator.compute(chapters, 8_000L)
        assertEquals(140_000L, result.estimatedMs)
        assertEquals(3, result.cachedTitles)
        assertEquals(2, result.unreadTitlesEstimated)
        assertEquals(1, result.titlesWithoutDuration)
        assertEquals(2, result.chaptersIncluded)
        assertEquals(0, result.chapterSizesGuessed)
        assertFalse(result.partialCache)
    }

    @Test fun reportsGuessesAndDoesNotInventVideoDurations() {
        val chapters = listOf(
            BacklogEstimator.Chapter(7, MediaType.MANGA, 70, null, 0),
            BacklogEstimator.Chapter(8, MediaType.VIDEO, 80, null, 0),
            BacklogEstimator.Chapter(9, MediaType.MANGA, 90, null, 0,
                titleProgress = 1f),
        )
        val result = BacklogEstimator.compute(chapters, 8_000L, partialCache = true)
        assertEquals(160_000L, result.estimatedMs)
        assertEquals(1, result.chapterSizesGuessed)
        assertEquals(1, result.unreadTitlesEstimated)
        assertEquals(1, result.titlesWithoutDuration)
        assertTrue(result.partialCache)
    }
}
