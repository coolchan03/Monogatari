package org.koitharu.kotatsu.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.videodl.OfflineVideoStore

/** The "Offline videos" row of the Storage overview: videos and audio saved for offline playback. */
class OfflineVideoStorage(context: Context) : StorageEntryProvider {

	private val app = context.applicationContext

	override suspend fun entries(): List<StorageEntry> = withContext(Dispatchers.IO) {
		val saved = OfflineVideoStore.list(app)
		listOf(
			StorageEntry(
				id = ID,
				bytes = totalBytes(saved.map { it.sizeBytes }),
				// MANAGE: asks first and says "Delete", but is never offered as an automatic low-storage clean-up.
				action = StorageAction.MANAGE,
				title = app.getString(R.string.storage_offline_videos),
				clear = { withContext(Dispatchers.IO) { OfflineVideoStore.list(app).forEach { OfflineVideoStore.remove(app, it) } } },
			),
		)
	}

	companion object {

		const val ID = "offline_videos"

		/** Sum of the entry sizes; unknown (negative) sizes count as 0. */
		fun totalBytes(sizes: List<Long>): Long = sizes.sumOf { it.coerceAtLeast(0L) }

		/** Registers once; calling it again replaces the earlier registration. */
		fun register(context: Context) {
			StorageEntryProviders.extra.removeAll { it is OfflineVideoStorage }
			StorageEntryProviders.extra.add(OfflineVideoStorage(context))
		}
	}
}
