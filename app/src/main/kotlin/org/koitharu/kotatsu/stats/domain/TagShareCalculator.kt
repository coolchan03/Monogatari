package org.koitharu.kotatsu.stats.domain

import org.koitharu.kotatsu.stats.data.TagDurationRow

/** Tag membership overlaps; individual shares are percentages of all reading, not a pie partition. */
data class TagShare(val title: String, val durationMs: Long, val percent: Double)

internal object TagShareCalculator {
    fun compute(rows: List<TagDurationRow>, overallMs: Long): List<TagShare> {
        if (overallMs <= 0L) return emptyList()
        return rows.asSequence()
            .filter { it.title.isNotBlank() && it.durationMs > 0L }
            .map { TagShare(it.title.trim(), it.durationMs,
                (it.durationMs.toDouble() / overallMs).coerceIn(0.0, 1.0)) }
            .sortedByDescending { it.durationMs }
            .toList()
    }
}
