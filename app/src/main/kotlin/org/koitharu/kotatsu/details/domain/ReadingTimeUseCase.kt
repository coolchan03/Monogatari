package org.koitharu.kotatsu.details.domain

import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.model.MangaHistory
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.details.data.ReadingTime
import org.koitharu.kotatsu.stats.data.StatsRepository
import org.koitharu.kotatsu.stats.domain.TimeEstimator
import javax.inject.Inject
import kotlin.math.roundToLong

/** Uses observed chapter sizes and a recent-weighted reading pace, with conservative fallbacks. */
class ReadingTimeUseCase @Inject constructor(
    private val settings: AppSettings,
    private val statsRepository: StatsRepository,
) {
    suspend operator fun invoke(manga: MangaDetails?, branch: String?, history: MangaHistory?): ReadingTime? {
        if (!settings.isReadingTimeEstimationEnabled || manga == null) return null
        val chapters = manga.chapters[branch].orEmpty()
        if (chapters.isEmpty()) return null

        val chapterIndex = history?.let { h ->
            chapters.indexOfFirst { it.id == h.chapterId }
        } ?: -1
        val isContinue = chapterIndex >= 0
        val start = chapterIndex.coerceAtLeast(0)
        // History.percent is a whole-title value; isolate the position within this chapter.
        val completedFraction = if (isContinue) {
            (history!!.percent.coerceIn(0f, 1f) * chapters.size - chapterIndex).coerceIn(0f, 1f)
        } else 0f

        val metadata = statsRepository.getChapterMetadata(manga.id)
        val kind = manga.toManga().source.mediaType()
        val chapterSizes = chapters.map { chapter -> metadata[chapter.id] }
        val estimateMs = when (kind) {
            MediaType.MANGA -> {
                val observed = chapterSizes.mapNotNull { it?.pages?.takeIf { count -> count > 0 } }
                val typicalPages = observed.takeIf { it.isNotEmpty() }
                    ?.average()?.roundToLong()?.toInt()?.coerceIn(1, 20000) ?: 20
                val remainingPages = chapters.indices.drop(start).sumOf { i ->
                    val count = chapterSizes[i]?.pages?.takeIf { it > 0 } ?: typicalPages
                    if (i == start && isContinue)
                        (count * (1.0 - completedFraction)).roundToLong() else count.toLong()
                }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val pace = if (settings.isStatsEnabled) statsRepository.getWeightedTimePerPage(manga.id)
                    else 8000L
                TimeEstimator.mangaRemainingMs(remainingPages, pace, null, null)
            }
            MediaType.BOOK -> {
                val observed = chapterSizes.mapNotNull { it?.words?.takeIf { count -> count > 0 } }
                val typicalWords = observed.takeIf { it.isNotEmpty() }
                    ?.average()?.roundToLong()?.toInt()?.coerceIn(1, 2_000_000) ?: 2500
                val words = chapters.indices.drop(start).sumOf { i ->
                    val count = chapterSizes[i]?.words?.takeIf { it > 0 } ?: typicalWords
                    if (i == start && isContinue)
                        (count * (1.0 - completedFraction)).roundToLong() else count.toLong()
                }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                TimeEstimator.wordsRemainingMs(words, null)
            }
            MediaType.VIDEO, MediaType.AUDIO -> {
                val observed = chapterSizes.mapNotNull { it?.durationMs?.takeIf { ms -> ms > 0 } }
                if (observed.isEmpty()) return null // no invented durations
                val typical = observed.average().roundToLong()
                chapters.indices.drop(start).sumOf { i ->
                    val duration = chapterSizes[i]?.durationMs?.takeIf { it > 0 } ?: typical
                    if (i == start && isContinue)
                        (duration * (1.0 - completedFraction)).roundToLong() else duration
                }
            }
        }
        val seconds = (estimateMs / 1000).coerceAtLeast(0)
        if (seconds < 60) return null
        return ReadingTime(
            minutes = ((seconds / 60) % 60).toInt(),
            hours = (seconds / 3600).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            isContinue = isContinue,
        )
    }
}
