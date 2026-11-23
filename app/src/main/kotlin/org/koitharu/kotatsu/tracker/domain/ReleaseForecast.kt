package org.koitharu.kotatsu.tracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One moment the tracker found new chapters for a title. */
data class ReleaseLog(val mangaId: Long, val title: String, val detectedAt: Long)

/**
 * Turns the tracker's history of "new chapters found" moments into a calendar of titles that are expected to
 * update soon. Only titles with enough history for [ReleaseRhythmEstimator] to trust appear; nothing is guessed.
 */
internal object ReleaseForecast {

	enum class Bucket { OVERDUE, TODAY, THIS_WEEK, LATER }

	data class Entry(
		val mangaId: Long,
		val title: String,
		val expectedAt: Long,
		val bucket: Bucket,
		/** Typical days between releases, at least 1. */
		val everyDays: Int,
	)

	private const val DAY_MS = 86_400_000L

	fun build(logs: List<ReleaseLog>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Entry> {
		val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
		return logs.groupBy { it.mangaId }.mapNotNull { (mangaId, items) ->
			// Several checks on one day are one release moment, otherwise a busy day would look like many.
			val dates = items.groupBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.detectedAt), zone) }
				.map { (_, sameDay) -> sameDay.maxOf { it.detectedAt } }
			val expected = ReleaseRhythmEstimator.nextExpectedAt(dates, now) ?: return@mapNotNull null
			val gap = ReleaseRhythmEstimator.typicalGapMs(dates, now) ?: return@mapNotNull null
			val expectedDay = LocalDate.ofInstant(Instant.ofEpochMilli(expected), zone)
			val bucket = when {
				expectedDay.isBefore(today) -> Bucket.OVERDUE
				expectedDay == today -> Bucket.TODAY
				expectedDay.isBefore(today.plusDays(7)) -> Bucket.THIS_WEEK
				else -> Bucket.LATER
			}
			Entry(
				mangaId = mangaId,
				title = items.maxBy { it.detectedAt }.title,
				expectedAt = expected,
				bucket = bucket,
				everyDays = (gap / DAY_MS).toInt().coerceAtLeast(1),
			)
		}.sortedWith(compareBy<Entry> { it.expectedAt }.thenBy { it.title.lowercase() })
	}
}
