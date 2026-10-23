package org.koitharu.kotatsu.recommend

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.BookShelf
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.OfficialEpubDownloader
import org.koitharu.kotatsu.core.media.PublicBookCatalog
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.awaitUniqueWorkInfoByName
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.download.ui.worker.DownloadTask
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.settings.work.PeriodicWorkScheduler
import org.koitharu.kotatsu.suggestions.domain.SuggestionRepository
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Opt-in, Wi-Fi only: queues the next chapters of favourites and suggestions through the regular
 * download system and saves a few Standard Ebooks EPUBs, all within the user's storage limit.
 */
@HiltWorker
class AutoDownloadWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	private val store: AutoDownloadStore,
	private val cleaner: AutoDownloadCleaner,
	private val settings: AppSettings,
	private val suggestionRepository: SuggestionRepository,
	private val favouritesRepository: FavouritesRepository,
	private val historyRepository: HistoryRepository,
	private val localMangaRepository: LocalMangaRepository,
	private val storageManager: LocalStorageManager,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val publicBookCatalog: PublicBookCatalog,
	@BaseHttpClient private val httpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

	override suspend fun doWork(): Result {
		if (!store.isEnabled) {
			return Result.success()
		}
		cleaner.reconcile()
		cleaner.trimToLimit()
		if (!hasFreeSpace()) {
			return Result.success()
		}
		val chapters = if (store.isMangaEnabled) {
			runCatchingCancellable { queueMangaChapters() }
				.onFailure(Throwable::printStackTraceDebug)
				.getOrDefault(0)
		} else {
			0
		}
		val books = if (store.isBooksEnabled) {
			runCatchingCancellable { saveBooks() }
				.onFailure(Throwable::printStackTraceDebug)
				.getOrDefault(0)
		} else {
			0
		}
		store.lastRunAt = System.currentTimeMillis()
		if (chapters > 0 || books > 0) {
			showSummary(chapters, books)
		}
		return Result.success()
	}

	private suspend fun queueMangaChapters(): Int {
		val entries = store.getEntries()
		var remaining = AutoDownloadPlanner.remainingBudget(store.storageLimitBytes, entries)
		val chapterBytes = AutoDownloadPlanner.estimateChapterBytes(entries)
		if (remaining < chapterBytes) {
			return 0
		}
		val excludeNsfw = settings.isNsfwContentDisabled || settings.isSuggestionsExcludeNsfw
		val known = entries.mapTo(HashSet()) { it.mangaId }
		val candidates = LinkedHashMap<Long, Manga>()
		// Favourites first: their unread chapters are the most likely to be read offline.
		favouritesRepository.getLastManga(FAVOURITES_LIMIT).forEach { candidates.putIfAbsent(it.id, it) }
		if (settings.isSuggestionsEnabled) {
			suggestionRepository.observeAll().first().take(SUGGESTIONS_LIMIT).forEach {
				candidates.putIfAbsent(it.id, it)
			}
		}
		val now = System.currentTimeMillis()
		val tasks = ArrayList<Pair<Manga, DownloadTask>>()
		val newEntries = ArrayList<AutoDownloadEntry>()
		var attempts = 0
		for (manga in candidates.values) {
			if (tasks.size >= MAX_TITLES_PER_RUN || attempts >= MAX_DETAILS_REQUESTS) {
				break
			}
			if (manga.id in known || manga.isLocal || manga.source.mediaType() != MediaType.MANGA) {
				continue
			}
			if (excludeNsfw && manga.isNsfw()) {
				continue
			}
			if (localMangaRepository.findSavedManga(manga, withDetails = false) != null) {
				continue // already downloaded (by the user or earlier): never mix with it
			}
			val count = AutoDownloadPlanner.chaptersWithinBudget(store.chaptersPerTitle, chapterBytes, remaining)
			if (count <= 0) {
				break
			}
			attempts++
			val details = runCatchingCancellable {
				mangaRepositoryFactory.create(manga.source).getDetails(manga)
			}.onFailure(Throwable::printStackTraceDebug).getOrNull() ?: continue
			if (excludeNsfw && details.isNsfw()) {
				continue
			}
			val history = historyRepository.getOne(details)
			val branch = details.getPreferredBranch(history)
			val chapterIds = details.chapters.orEmpty().filter { it.branch == branch }.map { it.id }
			val picked = AutoDownloadPlanner.pickChapters(chapterIds, history?.chapterId, emptySet(), count)
			if (picked.isEmpty()) {
				continue
			}
			val task = DownloadTask(
				mangaId = details.id,
				isPaused = false,
				isSilent = true,
				chaptersIds = picked.toLongArray(),
				destination = null,
				format = null,
				allowMeteredNetwork = false,
			)
			val estimate = picked.size * chapterBytes
			tasks.add(details to task)
			newEntries.add(
				AutoDownloadEntry(
					kind = AutoDownloadEntry.KIND_MANGA,
					title = details.title,
					mangaId = details.id,
					chapterIds = picked,
					bytes = estimate,
					isMeasured = false,
					createdAt = now,
				),
			)
			remaining -= estimate
		}
		if (tasks.isEmpty() || !store.isEnabled) {
			return 0 // opted out while we were looking up details: queue nothing
		}
		// Record ownership first so that work queued right before an interruption is still accounted.
		newEntries.forEach(store::add)
		try {
			downloadScheduler.schedule(tasks)
		} catch (e: Throwable) {
			store.remove(newEntries.mapTo(HashSet()) { it.key })
			throw e
		}
		return newEntries.sumOf { it.chapterIds.size }
	}

	private suspend fun saveBooks(): Int {
		val entries = store.getEntries()
		val unopenedBooks = entries.count { it.kind == AutoDownloadEntry.KIND_BOOK && !cleaner.isOpened(it) }
		val wanted = store.booksCount - unopenedBooks
		var remaining = AutoDownloadPlanner.remainingBudget(store.storageLimitBytes, entries)
		if (wanted <= 0 || remaining < AutoDownloadPlanner.DEFAULT_BOOK_BYTES) {
			return 0
		}
		val queries = runCatchingCancellable {
			suggestionRepository.getTopTags(2).map { it.title }
		}.getOrDefault(emptyList()) + ""
		val candidates = LinkedHashMap<String, PublicBookCatalog.Book>()
		for (query in queries) {
			runCatchingCancellable { publicBookCatalog.searchStandardEbooks(query) }
				.onFailure(Throwable::printStackTraceDebug)
				.getOrNull()
				?.forEach { candidates.putIfAbsent(it.url, it) }
			if (candidates.size >= wanted * 3) {
				break
			}
		}
		val libraryDir = File(applicationContext.filesDir, BOOKS_DIR)
		val existing = BookShelf.list(applicationContext)
		val existingFiles = existing.mapTo(HashSet()) { it.absolutePath }
		val existingTitles = existing.mapTo(HashSet()) { BookShelf.title(it).lowercase() }
		var saved = 0
		var attempts = 0
		for (book in candidates.values) {
			if (saved >= wanted || attempts >= wanted * 3 || remaining < AutoDownloadPlanner.DEFAULT_BOOK_BYTES) {
				break
			}
			if (book.title.lowercase() in existingTitles) {
				continue
			}
			attempts++
			val startedAt = System.currentTimeMillis()
			val file = runCatchingCancellable {
				val link = publicBookCatalog.resolveStandardEbooksEpub(book.url)
				if (link == null || !OfficialEpubDownloader.approved(book.catalog, link)) {
					null
				} else {
					OfficialEpubDownloader.save(httpClient, libraryDir, book.title, book.catalog, link)
				}
			}.onFailure(Throwable::printStackTraceDebug).getOrNull() ?: continue
			if (file.absolutePath in existingFiles) {
				continue // the user already had this one: it is not ours to account or evict
			}
			if (!AutoDownloadOwnership.isFreshlyCreated(file.lastModified(), startedAt)) {
				continue // it was there before this attempt (saved by the user meanwhile): not ours
			}
			val size = file.length()
			val entry = AutoDownloadEntry(
				kind = AutoDownloadEntry.KIND_BOOK,
				title = book.title,
				filePath = file.absolutePath,
				bytes = size,
				isMeasured = true,
				createdAt = System.currentTimeMillis(),
			)
			if (size > remaining) {
				// Never exceed the limit; re-check right now that the file is still ours and unopened.
				if (AutoDownloadOwnership.mayDiscardBook(file.lastModified(), startedAt, cleaner.isOpened(entry))) {
					file.delete()
				}
				continue
			}
			store.add(entry)
			remaining -= size
			saved++
		}
		return saved
	}

	private suspend fun hasFreeSpace(): Boolean = runCatchingCancellable {
		storageManager.computeAvailableSize() >= MIN_FREE_SPACE_BYTES
	}.getOrDefault(true)

	@SuppressLint("MissingPermission")
	private fun showSummary(chapters: Int, books: Int) {
		if (!applicationContext.checkNotificationPermission(CHANNEL_ID)) {
			return
		}
		val manager = NotificationManagerCompat.from(applicationContext)
		val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
			.setName(applicationContext.getString(R.string.auto_download_channel))
			.setShowBadge(false)
			.setVibrationEnabled(false)
			.setSound(null, null)
			.build()
		manager.createNotificationChannel(channel)
		val res = applicationContext.resources
		val chaptersText = res.getQuantityString(R.plurals.auto_download_chapters, chapters, chapters)
		val booksText = res.getQuantityString(R.plurals.auto_download_books, books, books)
		val text = when {
			books == 0 -> res.getString(R.string.auto_download_summary_chapters, chaptersText)
			chapters == 0 -> res.getString(R.string.auto_download_summary_books, booksText)
			else -> res.getString(R.string.auto_download_summary_both, chaptersText, booksText)
		}
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setContentTitle(applicationContext.getString(R.string.auto_download_recommendations))
			.setContentText(text)
			.setSmallIcon(android.R.drawable.stat_sys_download_done)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setSilent(true)
			.setAutoCancel(true)
			.setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					0,
					AppRouter.homeIntent(applicationContext),
					0,
					false,
				),
			)
			.build()
		manager.notify(NOTIFICATION_ID, notification)
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
		private val store: AutoDownloadStore,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.UNMETERED)
				.setRequiresBatteryNotLow(true)
				.setRequiresStorageNotLow(true)
				.setRequiresCharging(store.isChargingRequired)
				.build()
			val request = PeriodicWorkRequestBuilder<AutoDownloadWorker>(1, TimeUnit.DAYS)
				.setConstraints(constraints)
				.addTag(TAG)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
				.build()
			workManager
				.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
				.await()
		}

		override suspend fun unschedule() {
			workManager
				.cancelUniqueWork(TAG)
				.await()
		}

		override suspend fun isScheduled(): Boolean {
			return workManager
				.awaitUniqueWorkInfoByName(TAG)
				.any { !it.state.isFinished }
		}
	}

	private companion object {

		const val TAG = "monogatari_auto_download"
		const val CHANNEL_ID = "auto_download"
		const val NOTIFICATION_ID = 0x4d41_4444
		const val BOOKS_DIR = "imported-books"
		const val FAVOURITES_LIMIT = 30
		const val SUGGESTIONS_LIMIT = 30
		const val MAX_TITLES_PER_RUN = 6
		const val MAX_DETAILS_REQUESTS = 15
		const val MIN_FREE_SPACE_BYTES = 1L * 1024 * 1024 * 1024
	}
}
