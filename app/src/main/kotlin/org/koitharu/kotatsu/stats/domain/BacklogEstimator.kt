package org.koitharu.kotatsu.stats.domain

import org.koitharu.kotatsu.core.media.MediaType
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Cached chapter-only estimate. Never implies online titles have been fully discovered. */
internal object BacklogEstimator {
    data class Chapter(
        val mangaId: Long,
        val kind: MediaType,
        val chapterId: Long,
        val branch: String?,
        val index: Int,
        val pages: Int = 0,
        val words: Int = 0,
        val durationMs: Long = 0L,
        val currentChapterId: Long? = null,
        val titleProgress: Float? = null,
    )

    data class Summary(
        val estimatedMs: Long,
        val cachedTitles: Int,
        val unreadTitlesEstimated: Int,
        val chapterSizesGuessed: Int,
        val chaptersIncluded: Int,
        val titlesWithoutDuration: Int,
        val partialCache: Boolean,
    )

    fun compute(chapters: List<Chapter>, pagePaceMs: Long, partialCache: Boolean = false): Summary {
        var totalMs = 0L
        var unreadTitles = 0
        var guessed = 0
        var countedChapters = 0
        var missingDuration = 0
        val pace = pagePaceMs.takeIf { it in 1_000L..180_000L } ?: 8_000L
        val grouped = chapters.groupBy { it.mangaId }
        for (sameTitle in grouped.values) {
            val byBranch = sameTitle.groupBy { it.branch }
            val currentId = sameTitle.first().currentChapterId
            val preferred = byBranch.values.firstOrNull { group ->
                group.any { it.chapterId == currentId }
            } ?: byBranch.values.maxByOrNull { it.size } ?: continue
            val chapterList = preferred.sortedWith(compareBy<Chapter> { it.index }.thenBy { it.chapterId })
            val progress = sameTitle.first().titleProgress?.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f) ?: 0f
            if (progress >= 1f) continue
            val historyIndex = chapterList.indexOfFirst { it.chapterId == currentId }
            val start = if (historyIndex >= 0) historyIndex
                else (progress * chapterList.size).toInt().coerceIn(0, chapterList.lastIndex)
            val completedWithin = if (historyIndex >= 0)
                (progress * chapterList.size - historyIndex).coerceIn(0f, 1f).toDouble()
                else 0.0
            val kind = chapterList.first().kind
            val typicalPages = chapterList.map { it.pages }.filter { it > 0 }
                .let { if (it.isEmpty()) 20 else it.average().roundToInt().coerceAtLeast(1) }
            val typicalWords = chapterList.map { it.words }.filter { it > 0 }
                .let { if (it.isEmpty()) 2500 else it.average().roundToInt().coerceAtLeast(1) }
            val knownDurations = chapterList.map { it.durationMs }.filter { it > 0L }
            if (kind in listOf(MediaType.VIDEO, MediaType.AUDIO) && knownDurations.isEmpty()) {
                missingDuration++
                continue
            }
            val typicalDuration = if (knownDurations.isEmpty()) 0L
                else knownDurations.average().roundToLong().coerceAtLeast(1L)
            var titleMs = 0L
            for (i in start until chapterList.size) {
                val chapter = chapterList[i]
                val fraction = if (i == start) 1.0 - completedWithin else 1.0
                val rawMs = when (kind) {
                    MediaType.MANGA -> TimeEstimator.mangaRemainingMs(
                        chapter.pages.takeIf { it > 0 } ?: typicalPages, pace, null, null)
                    MediaType.BOOK -> TimeEstimator.wordsRemainingMs(
                        chapter.words.takeIf { it > 0 } ?: typicalWords, null)
                    MediaType.AUDIO, MediaType.VIDEO ->
                        chapter.durationMs.takeIf { it > 0L } ?: typicalDuration
                }
                if (fraction <= 0.0 || rawMs <= 0L) continue
                titleMs += (rawMs * fraction).roundToLong()
                countedChapters++
                if (when (kind) {
                    MediaType.MANGA -> chapter.pages <= 0
                    MediaType.BOOK -> chapter.words <= 0
                    MediaType.AUDIO, MediaType.VIDEO -> chapter.durationMs <= 0L
                }) guessed++
            }
            if (titleMs > 0L) {
                unreadTitles++
                totalMs += titleMs
            }
        }
        return Summary(totalMs, grouped.size, unreadTitles, guessed,
            countedChapters, missingDuration, partialCache)
    }
}
