package org.koitharu.kotatsu.details.domain

import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.details.data.ReadingTime
import org.koitharu.kotatsu.parsers.util.findById
import org.koitharu.kotatsu.stats.data.StatsRepository
import org.koitharu.kotatsu.stats.domain.TimeEstimator
import javax.inject.Inject
import kotlin.math.roundToInt

class ReadingTimeUseCase @Inject constructor(
	private val settings: AppSettings,
	private val statsRepository: StatsRepository,
) {

	suspend operator fun invoke(manga: MangaDetails?, branch: String?, history: MangaHistory?): ReadingTime? {
		if (!settings.isReadingTimeEstimationEnabled) {
			return null
		}
		val chapters = manga?.chapters?.get(branch)
		if (chapters.isNullOrEmpty()) {
			return null
		}
		val isOnHistoryBranch = history != null && chapters.findById(history.chapterId) != null
		// Until individual chapter sizes are available, use the measured reading pace
		// and the established 20-pages-per-chapter approximation.
		val estimatedPages = 20 * chapters.size
		var averageTimeSec = (TimeEstimator.mangaRemainingMs(
			estimatedPages, getMeasuredMsPerPage(manga.id), null, null,
		) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
		if (isOnHistoryBranch) {
			averageTimeSec = (averageTimeSec * (1f - history.percent)).roundToInt()
		}
		if (averageTimeSec < 60) {
			return null
		}
		return ReadingTime(
			minutes = (averageTimeSec / 60) % 60,
			hours = averageTimeSec / 3600,
			isContinue = isOnHistoryBranch,
		)
	}

	private suspend fun getMeasuredMsPerPage(mangaId: Long): Long? =
		if (settings.isStatsEnabled) statsRepository.getTimePerPage(mangaId).takeIf { it > 0L }
		else null
}
