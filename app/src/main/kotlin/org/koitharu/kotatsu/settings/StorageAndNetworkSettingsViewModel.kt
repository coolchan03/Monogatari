package org.koitharu.kotatsu.settings

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.runInterruptible
import okhttp3.Cache
import org.koitharu.kotatsu.core.media.BookShelf
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.computeSize
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.local.data.CacheDir
import org.koitharu.kotatsu.local.data.LocalStorageManager
import org.koitharu.kotatsu.local.domain.DeleteReadChaptersUseCase
import org.koitharu.kotatsu.recommend.AutoDownloadCleaner
import org.koitharu.kotatsu.recommend.AutoDownloadEntry
import org.koitharu.kotatsu.recommend.AutoDownloadStore
import org.koitharu.kotatsu.settings.userdata.storage.StorageUsage
import org.koitharu.kotatsu.storage.FeedCaches
import org.koitharu.kotatsu.storage.OfflineVideoStorage
import org.koitharu.kotatsu.storage.StorageAction
import org.koitharu.kotatsu.storage.StorageBreakdown
import org.koitharu.kotatsu.storage.StorageEntry
import org.koitharu.kotatsu.storage.StorageEntryProviders
import org.koitharu.kotatsu.storage.StorageOverview
import java.io.File
import javax.inject.Inject

@HiltViewModel
class StorageAndNetworkSettingsViewModel @Inject constructor(
    private val storageManager: LocalStorageManager,
    @ApplicationContext private val context: Context,
    private val httpCache: Cache,
    private val coil: ImageLoader,
    private val autoDownloadStore: AutoDownloadStore,
    private val autoDownloadCleaner: AutoDownloadCleaner,
    private val deleteReadChaptersUseCase: DeleteReadChaptersUseCase,
) : BaseViewModel() {

    val storageUsage: StateFlow<StorageUsage?> = flow {
        emit(loadStorageUsage())
    }.withErrorHandling()
        .stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(1000), null)

    private val overviewState = MutableStateFlow<StorageOverview?>(null)

    /** The Storage overview rows; null while the first measuring pass is running. */
    val overview: StateFlow<StorageOverview?> = overviewState.asStateFlow()

    private var overviewJob: Job? = null
    private var overviewLoadedAt = -OVERVIEW_TTL_MS

    init {
        OfflineVideoStorage.register(context)
    }

    /** Measures off the main thread. Without [force], a fresh result (or a running pass) is reused. */
    fun refreshOverview(force: Boolean = false) {
        if (!force) {
            val fresh = SystemClock.elapsedRealtime() - overviewLoadedAt < OVERVIEW_TTL_MS
            if (fresh || overviewJob?.isActive == true) {
                return
            }
        }
        overviewJob?.cancel()
        overviewJob = launchJob(Dispatchers.IO) {
            reloadOverview()
        }
    }

    /** Runs the clear / remove action of [entry], then measures again. */
    fun perform(entry: StorageEntry) {
        overviewJob?.cancel()
        overviewJob = launchJob(Dispatchers.IO) {
            try {
                clearEntry(entry)
            } finally {
                reloadOverview()
            }
        }
    }

    private suspend fun reloadOverview() {
        overviewState.value = loadOverview()
        overviewLoadedAt = SystemClock.elapsedRealtime()
    }

    private suspend fun clearEntry(entry: StorageEntry) {
        when (entry.id) {
            StorageEntry.PAGES_CACHE -> storageManager.clearCache(CacheDir.PAGES)

            StorageEntry.OTHER_CACHE -> {
                storageManager.clearCache(CacheDir.THUMBS)
                storageManager.clearCache(CacheDir.FAVICONS)
                storageManager.clearCache(CacheDir.TRANSLATED_PAGES)
                coil.memoryCache?.clear()
                runInterruptible { httpCache.evictAll() }
            }

            StorageEntry.AUTO_DOWNLOADS -> autoDownloadCleaner.removeAll()
            StorageEntry.FEED_CACHES -> FeedCaches.clear(context)
            StorageEntry.SAVED_MANGA -> deleteReadChaptersUseCase.invoke()
            else -> entry.clear?.invoke()
        }
    }

    private suspend fun loadOverview(): StorageOverview {
        val autoEntries = autoDownloadStore.getEntries()
        val autoManga = autoEntries.filter { it.kind == AutoDownloadEntry.KIND_MANGA }.sumOf { it.bytes }
        val autoBooks = autoEntries.filter { it.kind == AutoDownloadEntry.KIND_BOOK }.sumOf { it.bytes }
        val savedManga = storageManager.computeStorageSize()
        val books = BookShelf.list(context).sumOf { it.length() }
        val pagesCache = storageManager.computeCacheSize(CacheDir.PAGES)
        val allCaches = storageManager.computeCacheSize()
        val jsDir = File(context.filesDir, JS_SOURCES_DIR)
        val extensionCode = if (jsDir.isDirectory) jsDir.computeSize() else 0L
        val entries = ArrayList<StorageEntry>()
        // Auto-downloaded content lives inside the manga / book folders: show it once, as its own row.
        entries += StorageEntry(
            StorageEntry.SAVED_MANGA,
            StorageBreakdown.excluding(savedManga, autoManga),
            StorageAction.MANAGE,
        )
        entries += StorageEntry(
            StorageEntry.BOOKS,
            StorageBreakdown.excluding(books, autoBooks),
            StorageAction.OPEN_LIBRARY,
        )
        entries += StorageEntry(StorageEntry.PAGES_CACHE, pagesCache, StorageAction.CLEAR)
        entries += StorageEntry(
            StorageEntry.OTHER_CACHE,
            StorageBreakdown.excluding(allCaches, pagesCache),
            StorageAction.CLEAR,
        )
        entries += StorageEntry(
            StorageEntry.AUTO_DOWNLOADS,
            autoManga + autoBooks,
            StorageAction.REMOVE_AUTO_DOWNLOADS,
        )
        entries += StorageEntry(StorageEntry.FEED_CACHES, FeedCaches.measure(context), StorageAction.CLEAR)
        entries += StorageEntry(StorageEntry.EXTENSION_CODE, extensionCode, StorageAction.NONE)
        for (provider in StorageEntryProviders.extra) {
            try {
                entries += provider.entries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTraceDebug()
            }
        }
        val free = storageManager.computeAvailableSize()
        val total = context.filesDir.totalSpace.takeIf { it > 0L } ?: -1L
        return StorageOverview(entries, free, total)
    }

    private suspend fun loadStorageUsage(): StorageUsage {
        val pagesCacheSize = storageManager.computeCacheSize(CacheDir.PAGES)
        val otherCacheSize = storageManager.computeCacheSize() - pagesCacheSize
        val storageSize = storageManager.computeStorageSize()
        val availableSpace = storageManager.computeAvailableSize()
        val totalBytes = pagesCacheSize + otherCacheSize + storageSize + availableSpace
        return StorageUsage(
            savedManga = StorageUsage.Item(
                bytes = storageSize,
                percent = (storageSize.toDouble() / totalBytes).toFloat(),
            ),
            pagesCache = StorageUsage.Item(
                bytes = pagesCacheSize,
                percent = (pagesCacheSize.toDouble() / totalBytes).toFloat(),
            ),
            otherCache = StorageUsage.Item(
                bytes = otherCacheSize,
                percent = (otherCacheSize.toDouble() / totalBytes).toFloat(),
            ),
            available = StorageUsage.Item(
                bytes = availableSpace,
                percent = (availableSpace.toDouble() / totalBytes).toFloat(),
            ),
        )
    }

    private companion object {

        /** Folder of the installed JS extensions, see JsExtModule. */
        const val JS_SOURCES_DIR = "js_sources"

        /** A finished measuring pass is reused for this long when the screen is shown again. */
        const val OVERVIEW_TTL_MS = 10_000L
    }
}
