package org.koitharu.kotatsu.stats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StatsProgressMergeTest {
    private val newer = StatsEntity(mangaId = 12, startedAt = 1000L,
        duration = 145_000L, pages = 9, chapterId = 22L, words = 180, mediaMs = 700L)

    @Test fun olderCommitCannotEraseCumulativeCounts() {
        val stale = StatsEntity(12L, 1000L, 18_000L, 2, chapterId = 21L,
            words = 20, mediaMs = 300L)
        assertEquals(newer, newer.mergeSessionProgress(stale))
        assertEquals(newer, stale.mergeSessionProgress(newer))
    }

    @Test fun laterSnapshotPreservesNonzeroDimensions() {
        val future = StatsEntity(12L, 1000L, 200_000L, 10, chapterId = 23L,
            words = 0, mediaMs = 0L)
        val merged = newer.mergeSessionProgress(future)
        assertEquals(200_000L, merged.duration)
        assertEquals(10, merged.pages)
        assertEquals(23L, merged.chapterId)
        assertEquals(180, merged.words)
        assertEquals(700L, merged.mediaMs)
    }

    @Test fun rejectsDifferentSessionKeys() {
        assertThrows(IllegalArgumentException::class.java) {
            newer.mergeSessionProgress(newer.copy(startedAt = 2000L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            newer.mergeSessionProgress(newer.copy(mangaId = 99))
        }
    }
}
