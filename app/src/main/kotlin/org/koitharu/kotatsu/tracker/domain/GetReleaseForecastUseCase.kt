package org.koitharu.kotatsu.tracker.domain

import org.koitharu.kotatsu.core.db.MangaDatabase
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Titles expected to get new chapters soon, based on how often the tracker found them in the last months. */
internal class GetReleaseForecastUseCase @Inject constructor(
	private val database: MangaDatabase,
) {

	suspend operator fun invoke(now: Long = System.currentTimeMillis()): List<ReleaseForecast.Entry> {
		val rows = database.getTracksDao().findReleaseLogs(now - TimeUnit.DAYS.toMillis(HISTORY_DAYS))
		return ReleaseForecast.build(rows.map { ReleaseLog(it.mangaId, it.title, it.createdAt) }, now)
	}

	private companion object {

		const val HISTORY_DAYS = 180L
	}
}
