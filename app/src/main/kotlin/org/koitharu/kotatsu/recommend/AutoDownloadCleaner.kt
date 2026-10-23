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
		// Measure first (suspending), then merge into the ledger as it is by then, atomically.
		val results = HashMap<String, AutoDownloadEntry?>()
		for (entry in store.getEntries()) {
			results[entry.key] = try {
				reconcile(entry, now)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				e.printStackTraceDebug()
				entry
			}
		}
		store.update { AutoDownloadOwnership.mergeReconciled(it, results) }
		return store.getEntries()
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
		val removed = HashSet<String>()
		for (victim in victims) {
			if (delete(victim)) {
				removed.add(victim.key)
			}
		}
		store.remove(removed) // entries whose deletion failed stay in the ledger and are retried later
		return removed.size
	}

	/** User-requested: removes everything auto-downloaded, opened or not. Never touches user downloads. */
	suspend fun removeAll() {
		val removed = HashSet<String>()
		for (entry in store.getEntries()) {
			if (delete(entry)) {
				removed.add(entry.key)
			}
		}
		store.remove(removed)
	}

	/** Cancels the downloads this feature queued (not the user's own) and keeps the ledger as it is. */
	suspend fun cancelQueuedDownloads() {
		for (entry in store.getEntries()) {
			if (entry.kind == AutoDownloadEntry.KIND_MANGA) {
				cancelOwnedDownloads(entry)
			}
		}
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
					val ageMs = now - entry.createdAt
					entry.copy(
						bytes = AutoDownloadOwnership.accountedBytes(local.file.computeSize(), entry.bytes, entry.isMeasured, ageMs),
						isMeasured = !AutoDownloadOwnership.isStillInFlight(entry.isMeasured, ageMs),
					)
				} else if (isStale) {
					null // the download failed, was cancelled, or the user deleted it
				} else {
					entry
				}
			}
		}
	}

	/** Returns true when the content is gone (or was never there) so the ledger entry can be dropped. */
	private suspend fun delete(entry: AutoDownloadEntry): Boolean {
		return try {
			when (entry.kind) {
				AutoDownloadEntry.KIND_BOOK -> withContext(Dispatchers.IO) {
					val file = entry.filePath?.let { File(it) }
					file == null || !file.exists() || file.delete()
				}

				else -> deleteMangaChapters(entry)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			e.printStackTraceDebug()
			false
		}
	}

	private suspend fun deleteMangaChapters(entry: AutoDownloadEntry): Boolean {
		if (cancelOwnedDownloads(entry)) {
			return false // the user has downloads of this title in progress: leave everything alone
		}
		val manga = mangaDataRepository.findMangaById(entry.mangaId, withChapters = false) ?: return true
		val local = localMangaRepository.findSavedManga(manga, withDetails = true) ?: return true
		val localIds = local.manga.chapters?.ids().orEmpty()
		val toDelete = AutoDownloadOwnership.chaptersToDelete(localIds, entry.chapterIds.toSet())
		return when {
			toDelete == null -> localMangaRepository.delete(local.manga)
			// The user downloaded more chapters of this title themselves: keep those.
			toDelete.isNotEmpty() -> {
				localMangaRepository.deleteChapters(local.manga, toDelete)
				true
			}

			else -> true
		}
	}

	/**
	 * Cancels the unfinished downloads that this entry queued, recognised by their exact chapters.
	 * Returns true when other unfinished downloads of the same manga exist (not ours: left running).
	 */
	private suspend fun cancelOwnedDownloads(entry: AutoDownloadEntry): Boolean {
		var hasForeign = false
		val works = downloadScheduler.observeWorks().first()
		for (work in works) {
			if (work.state.isFinished) {
				continue
			}
			val task = downloadScheduler.getTask(work.id) ?: continue
			if (task.mangaId != entry.mangaId) {
				continue
			}
			if (AutoDownloadOwnership.ownsWork(entry, task.mangaId, task.chaptersIds?.toList(), task.isSilent)) {
				downloadScheduler.cancel(work.id)
			} else {
				hasForeign = true
			}
		}
		return hasForeign
	}

	private companion object {

		const val EPUB_PROGRESS_PREFS = "epub_progress"
		val PENDING_TIMEOUT_MS = TimeUnit.DAYS.toMillis(3)
	}
}
