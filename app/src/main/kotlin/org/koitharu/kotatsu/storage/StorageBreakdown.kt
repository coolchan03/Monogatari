package org.koitharu.kotatsu.storage

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** What the user can do with a row of the Storage overview. */
enum class StorageAction {

	/** Size only. */
	NONE,

	/** Safe to empty completely; counts for the low-storage recommendation. */
	CLEAR,

	/** Partial cleanup (for example deleting read chapters); never recommended automatically. */
	MANAGE,

	/** Opens the book library; no bulk delete here. */
	OPEN_LIBRARY,

	/** Removes only what the auto-download feature put on the device. */
	REMOVE_AUTO_DOWNLOADS,
}

/**
 * One row of the Storage overview. Built-in rows are told apart by [id] and get their title from the UI;
 * rows from a [StorageEntryProvider] bring their own [title] and [clear].
 */
data class StorageEntry(
	val id: String,
	val bytes: Long,
	val action: StorageAction,
	val title: String? = null,
	val clear: (suspend () -> Unit)? = null,
) {

	val isClearable: Boolean
		get() = bytes > 0L && (action == StorageAction.CLEAR || action == StorageAction.REMOVE_AUTO_DOWNLOADS)

	companion object {

		const val SAVED_MANGA = "saved_manga"
		const val BOOKS = "imported_books"
		const val PAGES_CACHE = "pages_cache"
		const val OTHER_CACHE = "other_cache"
		const val AUTO_DOWNLOADS = "auto_downloads"
		const val FEED_CACHES = "feed_caches"
		const val EXTENSION_CODE = "extension_code"
	}
}

/**
 * EXTENSION POINT: anything else that occupies space (for example offline videos, once that store exists)
 * implements this and registers itself in [StorageEntryProviders.extra]. Nothing here depends on such features.
 */
fun interface StorageEntryProvider {

	suspend fun entries(): List<StorageEntry>
}

object StorageEntryProviders {

	/** Add a provider here to get extra rows on the Storage screen. Empty by default. */
	val extra: MutableList<StorageEntryProvider> = CopyOnWriteArrayList()
}

/** Result of one measuring pass. [freeBytes] / [totalBytes] describe the device volume, -1 when unknown. */
data class StorageOverview(
	val entries: List<StorageEntry>,
	val freeBytes: Long,
	val totalBytes: Long,
) {

	val appBytes: Long
		get() = StorageBreakdown.totalBytes(entries)

	val biggestClearable: StorageEntry?
		get() = StorageBreakdown.biggestClearable(entries)

	val isLowStorage: Boolean
		get() = StorageBreakdown.isLowStorage(freeBytes)
}

/** Pure helpers of the Storage screen. */
object StorageBreakdown {

	const val LOW_STORAGE_BYTES: Long = 500L * 1024L * 1024L

	/** True when the free space is known and below [threshold]. */
	fun isLowStorage(freeBytes: Long, threshold: Long = LOW_STORAGE_BYTES): Boolean =
		freeBytes in 0 until threshold

	/** Size of a part that is counted separately taken out of a total that contains it; never negative. */
	fun excluding(total: Long, part: Long): Long = (total - part).coerceAtLeast(0L)

	fun totalBytes(entries: List<StorageEntry>): Long = entries.sumOf { it.bytes.coerceAtLeast(0L) }

	/** Share of [bytes] in [total] from 0 to 1. */
	fun shareFraction(bytes: Long, total: Long): Float {
		if (total <= 0L || bytes <= 0L) return 0f
		return (bytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
	}

	/** Share of [bytes] in [total] as a whole percent. */
	fun sharePercent(bytes: Long, total: Long): Int = Math.round(shareFraction(bytes, total) * 100f)

	/** The largest entry that can be emptied safely, or null when there is none. */
	fun biggestClearable(entries: List<StorageEntry>): StorageEntry? =
		entries.filter { it.isClearable }.maxByOrNull { it.bytes }

	/**
	 * "1.5 MB" style text; [units] are the unit names from the smallest (bytes) up, as in R.string.text_file_sizes.
	 */
	fun formatBytes(bytes: Long, units: List<String>, locale: Locale = Locale.getDefault()): String {
		if (bytes <= 0L || units.isEmpty()) {
			return "0 ${units.firstOrNull().orEmpty()}".trim()
		}
		var value = bytes.toDouble()
		var index = 0
		while (value >= 1024.0 && index < units.lastIndex) {
			value /= 1024.0
			index++
		}
		val number = DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(locale)).format(value)
		return "$number ${units[index]}"
	}
}
