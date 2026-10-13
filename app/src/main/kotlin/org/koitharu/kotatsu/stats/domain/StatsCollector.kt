package org.koitharu.kotatsu.stats.domain

import androidx.collection.LongSparseArray
import androidx.collection.set
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.RetainedLifecycleCoroutineScope
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.stats.data.StatsEntity
import org.koitharu.kotatsu.stats.data.StatsRepository
import javax.inject.Inject

@ViewModelScoped
class StatsCollector @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val repository: StatsRepository,
	lifecycle: ViewModelLifecycle,
) {

	private val viewModelScope = RetainedLifecycleCoroutineScope(lifecycle)
	private val stats = LongSparseArray<Entry>(1)
	private val recordedSizes = HashMap<Pair<Long, Long>, Triple<Int, Int, Long>>()

	@Synchronized
	fun onStateChanged(mangaId: Long, state: ReaderState) {
		if (!settings.isStatsEnabled) {
			stats.remove(mangaId)
			return
		}
		val now = System.currentTimeMillis()
		val entry = stats[mangaId]
		if (entry == null) {
			stats[mangaId] = Entry(
				lastEventAt = now,
				state = state,
				stats = StatsEntity(
					mangaId = mangaId,
					startedAt = now,
					duration = 0,
					pages = 0,
					chapterId = state.chapterId,
				),
			)
			return
		}
		val pagesDelta = if (entry.state.page != state.page || entry.state.chapterId != state.chapterId) 1 else 0
		val newEntry = entry.copy(
			lastEventAt = now,
			state = state,
			stats = StatsEntity(
				mangaId = mangaId,
				startedAt = entry.stats.startedAt,
				duration = entry.stats.duration + ReadingActivityTime.activeInterval(entry.lastEventAt, now),
				pages = entry.stats.pages + pagesDelta,
				chapterId = state.chapterId,
				words = entry.stats.words,
				mediaMs = entry.stats.mediaMs,
			),
		)
		stats[mangaId] = newEntry
		commit(newEntry.stats)
	}

	/** Records chapter dimensions from actual reader observations, not a fixed-size guess. */
	@Synchronized
	fun onChapterMetadata(mangaId: Long, chapterId: Long,
		pages: Int = 0, words: Int = 0, durationMs: Long = 0L) {
		if (!settings.isStatsEnabled || chapterId <= 0L) return
		if (pages <= 0 && words <= 0 && durationMs <= 0L) return
		val key = mangaId to chapterId
		val previous = recordedSizes[key]
		val sizes = Triple(maxOf(previous?.first ?: 0, pages),
			maxOf(previous?.second ?: 0, words),
			maxOf(previous?.third ?: 0L, durationMs))
		if (sizes == previous) return
		if (recordedSizes.size >= 200) recordedSizes.clear()
		recordedSizes[key] = sizes
		viewModelScope.launch(Dispatchers.IO) {
			runCatchingCancellable {
				repository.recordChapterMetadata(mangaId, chapterId,
					sizes.first, sizes.second, sizes.third)
			}.onFailure { it.printStackTraceDebug() }
		}
	}

	@Synchronized
	fun onPause(mangaId: Long) {
		stats.remove(mangaId)
	}

	private fun commit(entity: StatsEntity) {
		viewModelScope.launch(Dispatchers.Default) {
			runCatchingCancellable {
				db.getStatsDao().upsert(entity)
			}.onFailure { e ->
				e.printStackTraceDebug()
			}
		}
	}

	private data class Entry(
		val lastEventAt: Long,
		val state: ReaderState,
		val stats: StatsEntity,
	)
}
