package org.koitharu.kotatsu.backups.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.backups.data.model.ChapterMetaBackup
import org.koitharu.kotatsu.backups.data.model.StatisticBackup

class StatisticBackupMetadataTest {
    @Test fun oldStatisticsBackupHasSafeDimensionsDefaults() {
        val previous = """{"manga_id":19,"started_at":123456,"duration":60000,"pages":5}"""
        val stat = Json.decodeFromString<StatisticBackup>(previous)
        assertEquals(19L, stat.mangaId)
        assertEquals(null, stat.chapterId)
        assertEquals(0, stat.words)
        assertEquals(0L, stat.mediaMs)
    }

    @Test fun statisticsBackupPreservesChapterAndMediaDimensions() {
        val updated = StatisticBackup(
            mangaId = 19, startedAt = 123456, duration = 60000, pages = 5,
            chapterId = 777, words = 900, mediaMs = 120000,
        )
        val decoded = Json.decodeFromString<StatisticBackup>(Json.encodeToString(updated))
        assertEquals(777L, decoded.chapterId)
        assertEquals(900, decoded.words)
        assertEquals(120000L, decoded.mediaMs)
        assertEquals(777L, decoded.toEntity().chapterId)
    }

    @Test fun chapterMetadataBackupIsBackwardCompatibleAndRoundTrips() {
        val legacy = Json.decodeFromString<ChapterMetaBackup>(
            """{"chapter_id":77,"manga_id":19}"""
        )
        assertEquals(0, legacy.words)
        assertEquals(0L, legacy.durationMs)
        val enriched = ChapterMetaBackup(
            chapterId = 77, mangaId = 19, pages = 18, words = 1600,
            durationMs = 240000, updatedAt = 987654,
        )
        val restored = Json.decodeFromString<ChapterMetaBackup>(Json.encodeToString(enriched))
        assertEquals(18, restored.toEntity().pages)
        assertEquals(1600, restored.toEntity().words)
        assertEquals(240000L, restored.toEntity().durationMs)
    }
}
