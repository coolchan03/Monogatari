package org.koitharu.kotatsu.recommend

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.model.ids
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.util.ext.computeSize
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Measures, evicts and removes content that was put on the device by [AutoDownloadWorker] only. */
class AutoDownloadCleaner @Inject constructor(
	@ApplicationContext private val context: Context,
	private val store: AutoDownloadStore,
	private val localMangaRepository: LocalMangaRepository,
	private val mangaDataRepository: MangaDataRepository,
	private val historyRepository: HistoryRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
) {

	/** Replaces size estimates with the real size on disk and forgets entries whose files are gone. */
	suspend fun reconcile(now: Long = System.currentTimeMillis()): List<AutoDownloadEntry> {
		val original = store.getEntries()
		val updated = original.mapNotNull { entry ->
			try {
				reconcile(entry, now)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				e.printStackTraceDebug()
				entry
			}
		}
		return store.mergeReconciled(original, updated)
	}

	suspend fun isOpened(entry: AutoDownloadEntry): Boolean {
		try {
			if (entry.kind == AutoDownloadEntry.KIND_BOOK) {
				val name = entry.filePath?.let { File(it).name } ?: return false
				return context.getSharedPreferences(EPUB_PROGRESS_PREFS, Context.MODE_PRIVATE).contains(name)
			}
			val manga = mangaDataRepository.findMangaById(entry.mangaId, withChapters = false) ?: return false
			val history = historyRepository.getOne(manga)
			return history != null && history.updatedAt.toEpochMilli() > entry.createdAt
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			e.printStackTraceDebug()
			return true // when unsure, keep the content
		}
	}

	/** Evicts never-opened entries, oldest first, until usage fits the configured limit. */
	suspend fun trimToLimit(): Int {
		val entries = store.getEntries()
		val opened = HashMap<String, Boolean>()
		for (entry in entries) {
			opened[entry.key] = isOpened(entry)
		}
		val victims = AutoDownloadPlanner.selectForEviction(entries, store.storageLimitBytes) {
			opened[it.key] ?: true
		}
		for (victim in victims) {
			delete(victim)
		}
		store.remove(victims.mapTo(HashSet()) { it.key })
		return victims.size
	}

	/** User-requested: removes everything auto-downloaded, opened or not. Never touches user downloads. */
	suspend fun removeAll() {
		val entries = store.getEntries()
		cancelPendingDownloads(entries.filter { it.kind == AutoDownloadEntry.KIND_MANGA }.mapTo(HashSet()) { it.mangaId })
		for (entry in entries) {
			delete(entry)
		}
		store.remove(entries.mapTo(HashSet()) { it.key })
	}

	private suspend fun reconcile(entry: AutoDownloadEntry, now: Long): AutoDownloadEntry? {
		val isStale = now - entry.createdAt > PENDING_TIMEOUT_MS
		return when (entry.kind) {
			AutoDownloadEntry.KIND_BOOK -> {
				val file = entry.filePath?.let { File(it) }
				if (file != null && file.isFile) {
					entry.copy(bytes = file.length(), isMeasured = true)
				} else {
					null
				}
			}

			else -> {
				val manga = mangaDataRepository.findMangaById(entry.mangaId, withChapters = false)
					?: return if (isStale) null else entry
				val local = localMangaRepository.findSavedManga(manga, withDetails = false)
				if (local != null) {
					entry.copy(bytes = local.file.computeSize(), isMeasured = true)
				} else if (isStale) {
					null // the download failed, was cancelled, or the user deleted it
				} else {
					entry
				}
			}
		}
	}

	private suspend fun delete(entry: AutoDownloadEntry) {
		try {
			when (entry.kind) {
				AutoDownloadEntry.KIND_BOOK -> withContext(Dispatchers.IO) {
					entry.filePath?.let { File(it).delete() }
				}

				else -> deleteMangaChapters(entry)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			e.printStackTraceDebug()
		}
	}

	private suspend fun deleteMangaChapters(entry: AutoDownloadEntry) {
		cancelPendingDownloads(setOf(entry.mangaId))
		val manga = mangaDataRepository.findMangaById(entry.mangaId, withChapters = false) ?: return
		val local = localMangaRepository.findSavedManga(manga, withDetails = true) ?: return
		val localIds = local.manga.chapters?.ids().orEmpty()
		val ours = entry.chapterIds.toSet()
		if (localIds.isEmpty() || ours.containsAll(localIds)) {
			localMangaRepository.delete(local.manga)
		} else {
			// The user downloaded more chapters of this title themselves: keep those.
			val toDelete = localIds.filterTo(HashSet()) { it in ours }
			if (toDelete.isNotEmpty()) {
				localMangaRepository.deleteChapters(local.manga, toDelete)
			}
		}
	}

	private suspend fun cancelPendingDownloads(mangaIds: Set<Long>) {
		if (mangaIds.isEmpty()) {
			return
		}
		val works = downloadScheduler.observeWorks().first()
		for (work in works) {
			if (work.state.isFinished || AUTO_DOWNLOAD_WORK_TAG !in work.tags) {
				continue // Never cancel manual downloads that happen to be for the same manga.
			}
			val task = downloadScheduler.getTask(work.id) ?: continue
			if (task.mangaId in mangaIds) {
				downloadScheduler.cancel(work.id)
			}
		}
	}

	private companion object {

		const val EPUB_PROGRESS_PREFS = "epub_progress"
		val PENDING_TIMEOUT_MS = TimeUnit.DAYS.toMillis(3)
	}
}
