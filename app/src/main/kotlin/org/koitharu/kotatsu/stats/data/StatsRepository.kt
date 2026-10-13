package org.koitharu.kotatsu.stats.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.koitharu.kotatsu.stats.domain.StatsInsights
import org.koitharu.kotatsu.stats.domain.TagShare
import org.koitharu.kotatsu.stats.domain.TagShareCalculator
import org.koitharu.kotatsu.stats.domain.BacklogEstimator
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.media.persistedSourceNames
import org.koitharu.kotatsu.stats.domain.StatsRecord
import org.koitharu.kotatsu.stats.domain.StatsRollup
import java.util.TimeZone
import java.util.NavigableMap
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class StatsRepository @Inject constructor(
	private val settings: AppSettings,
	private val db: MangaDatabase,
) {

	suspend fun getReadingStats(period: StatsPeriod, categories: Set<Long>): List<StatsRecord> {
		val fromDate = if (period == StatsPeriod.ALL) {
			0L
		} else {
			System.currentTimeMillis() - TimeUnit.DAYS.toMillis(period.days.toLong())
		}
		val stats = db.getStatsDao().getDurationStats(fromDate, null, categories)
		val result = ArrayList<StatsRecord>(stats.size)
		var other = StatsRecord(null, 0)
		val total = stats.values.sum()
		for ((mangaEntity, duration) in stats) {
			val manga = mangaEntity.toManga(emptySet(), null)
			val percent = if (total > 0L) duration.toDouble() / total else 0.0
			if (percent < 0.05) {
				other = other.copy(duration = other.duration + duration)
			} else {
				result += StatsRecord(
					manga = manga,
					duration = duration,
				)
			}
		}
		if (other.duration != 0L) {
			result += other
		}
		return result
	}

	suspend fun getTimePerPage(mangaId: Long): Long = db.withTransaction {
		val dao = db.getStatsDao()
		val pages = dao.getReadPagesCount(mangaId)
		val time = if (pages >= 10) {
			dao.getAverageTimePerPage(mangaId)
		} else {
			dao.getAverageTimePerPage()
		}
		time
	}

	/** Transactionally merge dimensions; never replace a known size with zero. */
	suspend fun recordChapterMetadata(mangaId: Long, chapterId: Long,
		pages: Int = 0, words: Int = 0, durationMs: Long = 0L) {
		if (!settings.isStatsEnabled || mangaId <= 0L || chapterId <= 0L) return
		if (pages <= 0 && words <= 0 && durationMs <= 0L) return
		db.withTransaction {
			val dao = db.getStatsDao()
			val old = dao.getChapterMeta(mangaId, chapterId)
			val updated = ChapterMetaEntity(
				mangaId = mangaId, chapterId = chapterId,
				pages = maxOf(old?.pages ?: 0, pages.coerceIn(0, 20000)),
				words = maxOf(old?.words ?: 0, words.coerceIn(0, 2_000_000)),
				durationMs = maxOf(old?.durationMs ?: 0L, durationMs.coerceIn(0L, 86400000L)),
				updatedAt = System.currentTimeMillis(),
			)
			if (old == null || old.pages != updated.pages || old.words != updated.words ||
				old.durationMs != updated.durationMs) dao.upsertChapterMeta(updated)
		}
	}

	suspend fun getChapterMetadata(mangaId: Long): Map<Long, ChapterMetaEntity> =
		db.getStatsDao().findChapterMeta(mangaId).associateBy { it.chapterId }

	/** Title pace weighted 2× for sessions within 30 days, falling back to global. */
	suspend fun getWeightedTimePerPage(mangaId: Long): Long {
		val dao = db.getStatsDao()
		val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
		val title = dao.getWeightedTimePerPage(mangaId, since)
		if (title in 1000L..180000L) return title
		return dao.getWeightedGlobalTimePerPage(since).takeIf { it in 1000L..180000L } ?: 8000L
	}

	/** Reading time by local calendar day, suitable for a future statistics heatmap. */
	internal suspend fun getDailyReadingTime(days: Int, timezone: TimeZone = TimeZone.getDefault()): Map<StatsRollup.Day, Long> {
		require(days in 1..366)
		val fromDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
		return StatsRollup.byDay(db.getStatsDao().findSince(fromDate), timezone)
	}

	/** Last 90 days include the prior month for accurate current-month and streak figures. */
	internal suspend fun getInsights(goalMinutes: Int): StatsInsights.Summary {
		val now = System.currentTimeMillis()
		val since = now - TimeUnit.DAYS.toMillis(400L)
		return StatsInsights.compute(db.getStatsDao().findSince(since),
			now, TimeZone.getDefault(), goalMinutes)
	}

    /**
     * Inclusive tag shares: titles may have several tags, so percentages do not sum to 100%.
     * Reads local tag associations, not guesses based on source name or title keywords.
     */
    suspend fun getTagShares(period: StatsPeriod = StatsPeriod.MONTH): List<TagShare> {
        val now = System.currentTimeMillis()
        val since = if (period == StatsPeriod.ALL) 0L
            else now - TimeUnit.DAYS.toMillis(period.days.toLong())
        val dao = db.getStatsDao()
        return TagShareCalculator.compute(
            dao.getTagDurations(since, now, 12),
            dao.getPositiveDurationTotal(since, now),
        )
    }

    /**
     * Approximation restricted to locally cached chapters in active favourites.
     * Uncached library titles are deliberately excluded, never counted as zero backlog.
     */
    internal suspend fun getCachedBacklogEstimate(): BacklogEstimator.Summary {
        val dao = db.getStatsDao()
        val limit = 20_000
        val cached = dao.getCachedBacklogChapters(limit)
        val groups = MediaType.entries.associateWith { it.persistedSourceNames() }
        val chapters = cached.map { chapter ->
            val kind = when {
                chapter.source in groups.getValue(MediaType.VIDEO) -> MediaType.VIDEO
                chapter.source in groups.getValue(MediaType.AUDIO) -> MediaType.AUDIO
                chapter.source in groups.getValue(MediaType.BOOK) -> MediaType.BOOK
                else -> MediaType.MANGA
            }
            BacklogEstimator.Chapter(
                mangaId = chapter.mangaId, kind = kind, chapterId = chapter.chapterId,
                branch = chapter.branch, index = chapter.chapterIndex,
                pages = chapter.pages, words = chapter.words, durationMs = chapter.durationMs,
                currentChapterId = chapter.currentChapterId,
                titleProgress = chapter.progress,
            )
        }
        val recentSince = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30L)
        val pace = dao.getWeightedGlobalTimePerPage(recentSince)
        return BacklogEstimator.compute(chapters, pace, cached.size == limit)
    }

	/** Aggregate recorded time by current media source type (not guesses from file names). */
	suspend fun getMediaTotals(period: StatsPeriod = StatsPeriod.MONTH): Map<MediaType, Long> {
		val from = if (period == StatsPeriod.ALL) 0L
			else System.currentTimeMillis() - TimeUnit.DAYS.toMillis(period.days.toLong())
		val totals = mutableMapOf<MediaType, Long>()
		for ((entity, duration) in db.getStatsDao().getDurationStats(from, null, emptySet())) {
			val kind = entity.toManga(emptySet(), null).source.mediaType()
			totals[kind] = (totals[kind] ?: 0L) + duration.coerceAtLeast(0L)
		}
		return totals
	}

	suspend fun getTotalPagesRead(mangaId: Long): Int {
		return db.getStatsDao().getReadPagesCount(mangaId)
	}

	suspend fun getMangaTimeline(mangaId: Long): NavigableMap<Long, Int> {
		val entities = db.getStatsDao().findAll(mangaId)
		val map = TreeMap<Long, Int>()
		for (e in entities) {
			map[e.startedAt] = e.pages
		}
		return map
	}

	suspend fun clearStats() {
		db.getStatsDao().clear()
	}

	fun observeHasStats(mangaId: Long): Flow<Boolean> = settings.observeAsFlow(AppSettings.KEY_STATS_ENABLED) {
		isStatsEnabled
	}.flatMapLatest { isEnabled ->
		if (isEnabled) {
			db.getStatsDao().observeRowCount(mangaId).map { it > 0 }
		} else {
			flowOf(false)
		}
	}.distinctUntilChanged()
}
