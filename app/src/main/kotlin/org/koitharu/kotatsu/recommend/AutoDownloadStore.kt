package org.koitharu.kotatsu.recommend

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

internal const val AUTO_DOWNLOAD_WORK_TAG = "monogatari_auto_owned_download"

/**
 * Something the auto-download worker put on the device. Only these are ever evicted automatically;
 * downloads the user started are never recorded here.
 */
@Serializable
data class AutoDownloadEntry(
	val kind: String,
	val title: String,
	val mangaId: Long = 0L,
	val chapterIds: List<Long> = emptyList(),
	val filePath: String? = null,
	val bytes: Long,
	val isMeasured: Boolean = false,
	val createdAt: Long,
) {

	val key: String
		get() = if (kind == KIND_BOOK) "book:$filePath" else "manga:$mangaId"

	companion object {

		const val KIND_MANGA = "manga"
		const val KIND_BOOK = "book"
	}
}

/** Settings and bookkeeping of the opt-in recommendation auto-download, kept apart from AppSettings. */
@Singleton
class AutoDownloadStore @Inject constructor(
	@ApplicationContext context: Context,
) {

	val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	private val json = Json { ignoreUnknownKeys = true }
	private val entriesSerializer = ListSerializer(AutoDownloadEntry.serializer())
	private val lock = Any()

	val isEnabled: Boolean
		get() = prefs.getBoolean(KEY_ENABLED, false)

	var storageLimitMb: Int
		get() = prefs.getInt(KEY_STORAGE_LIMIT_MB, DEFAULT_STORAGE_LIMIT_MB)
		set(value) = prefs.edit { putInt(KEY_STORAGE_LIMIT_MB, value) }

	val storageLimitBytes: Long
		get() = storageLimitMb.toLong() * 1024L * 1024L

	var chaptersPerTitle: Int
		get() = prefs.getInt(KEY_CHAPTERS_PER_TITLE, DEFAULT_CHAPTERS_PER_TITLE)
		set(value) = prefs.edit { putInt(KEY_CHAPTERS_PER_TITLE, value) }

	var booksCount: Int
		get() = prefs.getInt(KEY_BOOKS_COUNT, DEFAULT_BOOKS_COUNT)
		set(value) = prefs.edit { putInt(KEY_BOOKS_COUNT, value) }

	var isMangaEnabled: Boolean
		get() = prefs.getBoolean(KEY_MEDIA_MANGA, true)
		set(value) = prefs.edit { putBoolean(KEY_MEDIA_MANGA, value) }

	var isBooksEnabled: Boolean
		get() = prefs.getBoolean(KEY_MEDIA_BOOKS, true)
		set(value) = prefs.edit { putBoolean(KEY_MEDIA_BOOKS, value) }

	/** Stored for the future; there is no offline video download path yet, so the worker ignores it. */
	var isVideoEnabled: Boolean
		get() = prefs.getBoolean(KEY_MEDIA_VIDEO, false)
		set(value) = prefs.edit { putBoolean(KEY_MEDIA_VIDEO, value) }

	/** Wi-Fi (unmetered) is always required; charging is the user's choice. */
	var isChargingRequired: Boolean
		get() = prefs.getBoolean(KEY_REQUIRE_CHARGING, false)
		set(value) = prefs.edit { putBoolean(KEY_REQUIRE_CHARGING, value) }

	var lastRunAt: Long
		get() = prefs.getLong(KEY_LAST_RUN, 0L)
		set(value) = prefs.edit { putLong(KEY_LAST_RUN, value) }

	fun getEntries(): List<AutoDownloadEntry> = synchronized(lock) {
		val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
		runCatching { json.decodeFromString(entriesSerializer, raw) }.getOrDefault(emptyList())
	}

	fun setEntries(entries: List<AutoDownloadEntry>) = synchronized(lock) {
		prefs.edit { putString(KEY_ENTRIES, json.encodeToString(entriesSerializer, entries)) }
	}

	/** Merge a measured snapshot without losing records changed by another worker during I/O. */
	fun mergeReconciled(original: List<AutoDownloadEntry>, measured: List<AutoDownloadEntry>): List<AutoDownloadEntry> = synchronized(lock) {
		val originals = original.associateBy { it.key }
		val updated = measured.associateBy { it.key }
		val merged = getEntries().mapNotNull { current ->
			val previous = originals[current.key] ?: return@mapNotNull current
			if (current != previous) current else updated[current.key]
		}
		setEntries(merged)
		merged
	}

	fun add(entry: AutoDownloadEntry) = synchronized(lock) {
		setEntries(getEntries().filterNot { it.key == entry.key } + entry)
	}

	fun remove(keys: Set<String>) = synchronized(lock) {
		setEntries(getEntries().filterNot { it.key in keys })
	}

	fun getUsedBytes(): Long = AutoDownloadPlanner.usedBytes(getEntries())

	companion object {

		const val PREFS_NAME = "monogatari_auto_download"

		const val KEY_ENABLED = "enabled"
		const val KEY_STORAGE_LIMIT_MB = "storage_limit_mb"
		const val KEY_CHAPTERS_PER_TITLE = "chapters_per_title"
		const val KEY_BOOKS_COUNT = "books_count"
		const val KEY_MEDIA_MANGA = "media_manga"
		const val KEY_MEDIA_BOOKS = "media_books"
		const val KEY_MEDIA_VIDEO = "media_video"
		const val KEY_REQUIRE_CHARGING = "require_charging"
		const val KEY_ENTRIES = "entries"
		const val KEY_LAST_RUN = "last_run"

		const val DEFAULT_STORAGE_LIMIT_MB = 1024
		const val DEFAULT_CHAPTERS_PER_TITLE = 3
		const val DEFAULT_BOOKS_COUNT = 1

		val STORAGE_LIMIT_OPTIONS_MB = intArrayOf(500, 1024, 2048, 5120)
		val CHAPTERS_OPTIONS = intArrayOf(1, 3, 5, 10)
		val BOOKS_OPTIONS = intArrayOf(1, 3, 5)
	}
}
