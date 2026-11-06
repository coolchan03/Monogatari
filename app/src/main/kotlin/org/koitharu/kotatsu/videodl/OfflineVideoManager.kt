package org.koitharu.kotatsu.videodl

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloaderFactory
import org.koitharu.kotatsu.R
import java.io.File
import java.util.concurrent.Executor

/**
 * Process-wide owner of the offline cache and the Media3 [DownloadManager]. The cache lives in the
 * app's private files dir (never the user's media folders), never evicts on its own, and new
 * downloads are refused once it reaches [MAX_CACHE_BYTES].
 */
@OptIn(UnstableApi::class)
object OfflineVideoManager {

	const val MAX_CACHE_BYTES = 4L * 1024 * 1024 * 1024
	private const val CACHE_DIR = "offline_video"

	private var databaseProvider: StandaloneDatabaseProvider? = null
	private var cache: SimpleCache? = null
	private var manager: DownloadManager? = null

	@Synchronized
	fun cache(context: Context): Cache {
		cache?.let { return it }
		val app = context.applicationContext
		val provider = databaseProvider ?: StandaloneDatabaseProvider(app).also { databaseProvider = it }
		return SimpleCache(File(app.filesDir, CACHE_DIR), NoOpCacheEvictor(), provider).also { cache = it }
	}

	@Synchronized
	fun manager(context: Context): DownloadManager {
		manager?.let { return it }
		val app = context.applicationContext
		val store = cache(app)
		val index = DefaultDownloadIndex(databaseProvider!!)
		val executor = Executor { it.run() }
		val factory = DownloaderFactory { request ->
			val headers = OfflineVideoStore.get(app, OfflineVideoStore.entryIdOf(request.id))?.headers.orEmpty()
			val upstream = DefaultHttpDataSource.Factory()
				.setAllowCrossProtocolRedirects(true)
				.setDefaultRequestProperties(headers)
			DefaultDownloaderFactory(
				CacheDataSource.Factory().setCache(store).setUpstreamDataSourceFactory(upstream),
				executor,
			).createDownloader(request)
		}
		return DownloadManager(app, index, factory).also {
			it.addListener(StoreSyncListener(app))
			manager = it
		}
	}

	/** True once the cache is full; the service stops running downloads and new ones are refused. */
	fun isFull(context: Context): Boolean = cache(context).cacheSpace >= MAX_CACHE_BYTES

	/** Asks Media3 to delete the downloaded data of an entry (video part and audio part). */
	fun removeData(context: Context, entryId: String) {
		val downloads = manager(context)
		val remove = {
			downloads.removeDownload(entryId)
			downloads.removeDownload(OfflineVideoStore.audioPartId(entryId))
		}
		// DownloadManager is not thread safe: callers on a background thread (Storage screen, Library) hop to main.
		if (Looper.myLooper() == Looper.getMainLooper()) remove() else Handler(Looper.getMainLooper()).post { remove() }
	}

	/** Keeps [OfflineVideoStore] in step with what Media3 finished, failed or removed. */
	private class StoreSyncListener(private val app: Context) : DownloadManager.Listener {

		override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
			val entryId = OfflineVideoStore.entryIdOf(download.request.id)
			when (download.state) {
				Download.STATE_COMPLETED -> markCompletedIfDone(downloadManager, entryId)
				Download.STATE_FAILED -> {
					val title = OfflineVideoStore.get(app, entryId)?.title.orEmpty()
					toast(app.getString(R.string.offline_video_failed, title))
					downloadManager.removeDownload(entryId)
					downloadManager.removeDownload(OfflineVideoStore.audioPartId(entryId))
				}
			}
		}

		override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
			val entryId = OfflineVideoStore.entryIdOf(download.request.id)
			OfflineVideoStore.forget(app, entryId)
			// The video and audio parts live and die together.
			val partner = if (download.request.id == entryId) OfflineVideoStore.audioPartId(entryId) else entryId
			downloadManager.removeDownload(partner)
		}

		private fun markCompletedIfDone(downloadManager: DownloadManager, entryId: String) {
			val entry = OfflineVideoStore.get(app, entryId) ?: return
			val parts = listOfNotNull(entryId, entry.audioUrl?.let { OfflineVideoStore.audioPartId(entryId) })
			val done = parts.mapNotNull { id ->
				runCatching { downloadManager.downloadIndex.getDownload(id) }.getOrNull()
					?.takeIf { it.state == Download.STATE_COMPLETED }
			}
			if (done.size != parts.size) return
			val size = done.sumOf { it.bytesDownloaded }
			OfflineVideoStore.update(app, entryId) { it.copy(completed = true, sizeBytes = size) }
			toast(app.getString(R.string.offline_video_saved, entry.title))
		}

		private fun toast(text: String) {
			Toast.makeText(app, text, Toast.LENGTH_LONG).show()
		}
	}
}
