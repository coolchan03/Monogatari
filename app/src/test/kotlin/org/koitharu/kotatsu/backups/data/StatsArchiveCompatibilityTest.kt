package org.koitharu.kotatsu.backups.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.backups.data.model.ChapterMetaBackup
import org.koitharu.kotatsu.backups.data.model.StatisticBackup
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.stats.data.ChapterMetaEntity
import org.koitharu.kotatsu.stats.data.StatsEntity
import java.util.zip.ZipEntry

class StatsArchiveCompatibilityTest {
    @Test fun legacyStatsWithoutOptionalColumnsAreStillReadable() {
        val old = """{"manga_id":1,"started_at":123456,"duration":75000,"pages":5}"""
        val decoded = Json.decodeFromString<StatisticBackup>(old)
        assertNull(decoded.chapterId)
        assertEquals(0, decoded.words)
        assertEquals(0L, decoded.mediaMs)
        assertEquals(75000L, decoded.toEntity().duration)
    }

    @Test fun newStatsPreserveChapterWordsAndMediaTime() {
        val original = StatsEntity(mangaId = 19L, startedAt = 123456L, duration = 60000L,
            pages = 4, chapterId = 20L, words = 800, mediaMs = 45000L)
        val decoded = Json.decodeFromString<StatisticBackup>(
            Json.encodeToString(StatisticBackup(original)))
        assertEquals(original, decoded.toEntity())
    }

    @Test fun chapterMetadataRoundTripsAndSectionIsDiscoverable() {
        val entity = ChapterMetaEntity(chapterId = 111, mangaId = 222, pages = 48,
            words = 900, durationMs = 240000, updatedAt = 12345)
        val decoded = Json.decodeFromString<ChapterMetaBackup>(
            Json.encodeToString(ChapterMetaBackup(entity)))
        assertEquals(entity, decoded.toEntity())
        assertEquals(BackupSection.CHAPTER_META, BackupSection.of(ZipEntry("chapter_meta")))
    }
}
