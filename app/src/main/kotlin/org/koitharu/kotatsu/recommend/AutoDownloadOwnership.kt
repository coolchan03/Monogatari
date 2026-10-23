package org.koitharu.kotatsu.recommend

import java.util.concurrent.TimeUnit

/** Pure rules deciding what auto-download may cancel, delete or count (no Android dependencies). */
object AutoDownloadOwnership {

	/** File systems may round modification times; a file older than the attempt start by more is not ours. */
	private const val MTIME_SLACK_MS = 2_000L

	/** Estimates are trusted over partial on-disk sizes for this long after queueing. */
	private val IN_FLIGHT_WINDOW_MS = TimeUnit.DAYS.toMillis(1)

	/**
	 * The download scheduler returns no job ids, so a queued job is recognised by what we queued:
	 * a silent task for the same manga with exactly the chapters recorded in the ledger entry.
	 * Anything else (a manual download of the same title) is never ours.
	 */
	fun ownsWork(
		entry: AutoDownloadEntry,
		workMangaId: Long,
		workChapterIds: Collection<Long>?,
		workIsSilent: Boolean,
	): Boolean = entry.kind == AutoDownloadEntry.KIND_MANGA &&
		workIsSilent &&
		workMangaId == entry.mangaId &&
		!workChapterIds.isNullOrEmpty() &&
		workChapterIds.toSet() == entry.chapterIds.toSet()

	/**
	 * Which local chapters of a title may be deleted. Null means the whole title (nothing but our
	 * chapters is there); otherwise only the returned chapters, possibly none.
	 */
	fun chaptersToDelete(localIds: Collection<Long>, ours: Set<Long>): Set<Long>? {
		if (localIds.isEmpty() || ours.containsAll(localIds)) {
			return null
		}
		return localIds.filterTo(HashSet()) { it in ours }
	}

	/** True when a book file was written by the attempt that started at [startedAt], not already there. */
	fun isFreshlyCreated(lastModified: Long, startedAt: Long): Boolean = lastModified >= startedAt - MTIME_SLACK_MS

	/** A book may be removed again only when this run created it and nobody has opened it since. */
	fun mayDiscardBook(lastModified: Long, startedAt: Long, isOpened: Boolean): Boolean =
		!isOpened && isFreshlyCreated(lastModified, startedAt)

	/**
	 * Bytes to count for a manga entry. A measured size is final; while the download may still be
	 * running the larger of the estimate and the partial on-disk size counts, so the budget is not
	 * under-used because a transfer is half done.
	 */
	fun accountedBytes(actual: Long, estimate: Long, wasMeasured: Boolean, ageMs: Long): Long = when {
		wasMeasured || ageMs > IN_FLIGHT_WINDOW_MS -> actual
		else -> maxOf(actual, estimate)
	}

	/** True when the entry is still counted by estimate (queued, not yet settled). */
	fun isStillInFlight(wasMeasured: Boolean, ageMs: Long): Boolean = !wasMeasured && ageMs <= IN_FLIGHT_WINDOW_MS

	/**
	 * Applies reconcile [results] (null value = forget the entry) to the ledger as it is now.
	 * Entries added meanwhile have no result and are kept; entries removed meanwhile stay removed.
	 */
	fun mergeReconciled(
		current: List<AutoDownloadEntry>,
		results: Map<String, AutoDownloadEntry?>,
	): List<AutoDownloadEntry> = current.mapNotNull { entry ->
		if (results.containsKey(entry.key)) {
			results[entry.key]?.let { entry.copy(bytes = it.bytes, isMeasured = it.isMeasured) }
		} else {
			entry
		}
	}
}
