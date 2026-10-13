package org.koitharu.kotatsu.stats.data

import androidx.room.Dao
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import kotlin.collections.forEach

@Dao
abstract class StatsDao {

	@Query("SELECT * FROM stats WHERE manga_id = :mangaId ORDER BY started_at")
	abstract suspend fun findAll(mangaId: Long): List<StatsEntity>

	@Query("SELECT * FROM stats WHERE started_at >= :fromDate ORDER BY started_at")
	abstract suspend fun findSince(fromDate: Long): List<StatsEntity>

    @Query("SELECT IFNULL(SUM(duration), 0) FROM stats WHERE started_at BETWEEN :fromDate AND :through AND duration > 0")
    abstract suspend fun getPositiveDurationTotal(fromDate: Long, through: Long): Long

    /** Normalize duplicate source tag names per title before summing reading time. */
    @Query("""
        SELECT MIN(t.title) AS title, SUM(s.durationMs) AS durationMs
        FROM (
            SELECT manga_tags.manga_id AS mangaId,
                LOWER(TRIM(tags.title)) AS tagKey, MIN(TRIM(tags.title)) AS title
            FROM manga_tags JOIN tags ON tags.tag_id = manga_tags.tag_id
            WHERE TRIM(tags.title) != ''
            GROUP BY manga_tags.manga_id, LOWER(TRIM(tags.title))
        ) t
        JOIN (
            SELECT manga_id AS mangaId, SUM(duration) AS durationMs
            FROM stats WHERE started_at BETWEEN :fromDate AND :through AND duration > 0
            GROUP BY manga_id
        ) s ON s.mangaId = t.mangaId
        GROUP BY t.tagKey
        ORDER BY durationMs DESC LIMIT :limit
    """)
    abstract suspend fun getTagDurations(
        fromDate: Long, through: Long, limit: Int,
    ): List<TagDurationRow>

    /** Only locally cached chapter lists; no unverified online chapter counts. */
    @Query("""
        SELECT c.manga_id AS mangaId, m.source AS source, c.chapter_id AS chapterId,
            c.branch AS branch, c.`index` AS chapterIndex,
            IFNULL(meta.pages,0) AS pages, IFNULL(meta.words,0) AS words,
            IFNULL(meta.duration_ms,0) AS durationMs,
            h.chapter_id AS currentChapterId, h.percent AS progress
        FROM chapters c INNER JOIN manga m ON m.manga_id = c.manga_id
        LEFT JOIN chapter_meta meta ON meta.manga_id = c.manga_id
            AND meta.chapter_id = c.chapter_id
        LEFT JOIN history h ON h.manga_id = c.manga_id AND h.deleted_at = 0
        WHERE EXISTS (SELECT 1 FROM favourites f WHERE f.manga_id = c.manga_id AND f.deleted_at = 0)
        ORDER BY c.manga_id, c.branch, c.`index`
        LIMIT :limit
    """)
    abstract suspend fun getCachedBacklogChapters(limit: Int): List<CachedBacklogChapter>

	@Query("SELECT IFNULL(SUM(pages),0) FROM stats WHERE manga_id = :mangaId")
	abstract suspend fun getReadPagesCount(mangaId: Long): Int

	@Query("SELECT IFNULL(SUM(duration)/SUM(pages), 0) FROM stats WHERE manga_id = :mangaId")
	abstract suspend fun getAverageTimePerPage(mangaId: Long): Long

	@Query("SELECT IFNULL(SUM(duration)/SUM(pages), 0) FROM stats")
	abstract suspend fun getAverageTimePerPage(): Long

	@Query("SELECT * FROM chapter_meta WHERE manga_id = :mangaId AND chapter_id = :chapterId LIMIT 1")
	abstract suspend fun getChapterMeta(mangaId: Long, chapterId: Long): ChapterMetaEntity?

	@Query("SELECT * FROM chapter_meta WHERE manga_id = :mangaId")
	abstract suspend fun findChapterMeta(mangaId: Long): List<ChapterMetaEntity>

	@Upsert
	abstract suspend fun upsertChapterMeta(metadata: ChapterMetaEntity)

	/** Weighted toward sessions in the past 30 days. Exclude video/audio sessions. */
	@Query("""SELECT IFNULL(
	    SUM(CASE WHEN started_at >= :recentSince THEN duration * 2 ELSE duration END) /
	    NULLIF(SUM(CASE WHEN started_at >= :recentSince THEN pages * 2 ELSE pages END),0),0)
	    FROM stats WHERE manga_id = :mangaId AND pages > 0 AND duration > 0""")
	abstract suspend fun getWeightedTimePerPage(mangaId: Long, recentSince: Long): Long

	@Query("""SELECT IFNULL(
	    SUM(CASE WHEN started_at >= :recentSince THEN duration * 2 ELSE duration END) /
	    NULLIF(SUM(CASE WHEN started_at >= :recentSince THEN pages * 2 ELSE pages END),0),0)
	    FROM stats WHERE pages > 0 AND duration > 0""")
	abstract suspend fun getWeightedGlobalTimePerPage(recentSince: Long): Long

	@Query("DELETE FROM stats")
	abstract suspend fun clear()

	@Query("SELECT COUNT(*) FROM stats WHERE manga_id = :mangaId")
	abstract fun observeRowCount(mangaId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMissing(entity: StatsEntity)

    @Query("SELECT * FROM stats WHERE manga_id = :mangaId AND started_at = :startedAt LIMIT 1")
    protected abstract suspend fun findProgressRow(mangaId: Long, startedAt: Long): StatsEntity?

    /**
     * Pending coroutine writes may finish out of order. A transaction and monotonic merge
     * ensure a stale page event cannot erase newer duration or chapter progress.
     * Restore still uses its separate insertMissing path.
     */
    @androidx.room.Transaction
    open suspend fun upsertProgress(entity: StatsEntity) {
        val current = findProgressRow(entity.mangaId, entity.startedAt)
        upsert(current?.mergeSessionProgress(entity) ?: entity)
    }

	@Upsert
	abstract suspend fun upsert(entity: StatsEntity)

	suspend fun getDurationStats(
		fromDate: Long,
		isNsfw: Boolean?,
		favouriteCategories: Set<Long>
	): Map<MangaEntity, Long> {
		val conditions = ArrayList<String>()
        conditions.add("stats.started_at >= $fromDate")
        conditions.add("stats.started_at <= ${System.currentTimeMillis()}")
        conditions.add("stats.duration > 0")
		if (favouriteCategories.isNotEmpty()) {
			val ids = favouriteCategories.joinToString(",")
			conditions.add("stats.manga_id IN (SELECT manga_id FROM favourites WHERE category_id IN ($ids))")
		}
		if (isNsfw != null) {
			val flag = if (isNsfw) 1 else 0
			conditions.add("manga.nsfw = $flag")
		}
		val where = conditions.joinToString(separator = " AND ")
		val query = SimpleSQLiteQuery(
			"SELECT manga.*, SUM(duration) AS d FROM stats INNER JOIN manga ON manga.manga_id = stats.manga_id WHERE $where GROUP BY manga.manga_id ORDER BY d DESC",
		)
		return getDurationStatsImpl(query)
	}

	@RawQuery
	protected abstract suspend fun getDurationStatsImpl(
		query: SupportSQLiteQuery
	): Map<@MapColumn("manga") MangaEntity, @MapColumn("d") Long>

    /**
     * Cursor pagination avoids shifting OFFSET windows when the reader writes new metadata
     * while a backup is being streamed. The same key is never emitted twice.
     */
    @Query("""
        SELECT * FROM chapter_meta
        WHERE manga_id > :afterManga
            OR (manga_id = :afterManga AND chapter_id > :afterChapter)
        ORDER BY manga_id, chapter_id LIMIT :limit
    """)
    protected abstract suspend fun chapterMetaPage(
        afterManga: Long, afterChapter: Long, limit: Int,
    ): List<ChapterMetaEntity>

    fun dumpChapterMeta(): Flow<ChapterMetaEntity> = flow {
        var lastManga = -1L
        var lastChapter = -1L
        while (currentCoroutineContext().isActive) {
            val page = chapterMetaPage(lastManga, lastChapter, 100)
            if (page.isEmpty()) break
            page.forEach { emit(it) }
            lastManga = page.last().mangaId
            lastChapter = page.last().chapterId
        }
    }

    @Query("""
        SELECT * FROM stats
        WHERE started_at > :afterStarted
            OR (started_at = :afterStarted AND manga_id > :afterManga)
        ORDER BY started_at, manga_id LIMIT :limit
    """)
    protected abstract suspend fun statsPage(
        afterStarted: Long, afterManga: Long, limit: Int,
    ): List<StatsEntity>

    fun dumpEnabled(): Flow<StatsEntity> = flow {
        var lastStarted = -1L
        var lastManga = -1L
        while (currentCoroutineContext().isActive) {
            val page = statsPage(lastStarted, lastManga, 100)
            if (page.isEmpty()) break
            page.forEach { emit(it) }
            lastStarted = page.last().startedAt
            lastManga = page.last().mangaId
        }
    }
}
