package org.koitharu.kotatsu.stats.domain

import kotlin.math.roundToLong

/** Pure estimation arithmetic. Source-specific chapter metadata is integrated separately. */
internal object TimeEstimator {
    private const val FALLBACK_MANGA_MS_PER_PAGE = 8_000L
    private const val FALLBACK_WORDS_PER_MINUTE = 230.0

    fun mangaRemainingMs(remainingPages: Int, titleMsPerPage: Long?,
        mediaMsPerPage: Long?, globalMsPerPage: Long?): Long {
        val pace = sequenceOf(titleMsPerPage, mediaMsPerPage, globalMsPerPage)
            .filterNotNull().firstOrNull { it > 0 } ?: FALLBACK_MANGA_MS_PER_PAGE
        return remainingPages.coerceAtLeast(0).toLong() * pace
    }

    fun wordsRemainingMs(remainingWords: Int, wordsPerMinute: Double?): Long {
        val speed = wordsPerMinute?.takeIf { it.isFinite() && it > 0 }
            ?: FALLBACK_WORDS_PER_MINUTE
        return (remainingWords.coerceAtLeast(0) * 60_000.0 / speed).roundToLong()
    }

    fun mediaRemainingMs(durationMs: Long, elapsedMs: Long): Long =
        (durationMs - elapsedMs.coerceAtLeast(0)).coerceAtLeast(0)
}
