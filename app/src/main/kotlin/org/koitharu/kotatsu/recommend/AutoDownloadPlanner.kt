package org.koitharu.kotatsu.recommend

/** Pure selection and eviction rules for recommendation auto-download (no Android dependencies). */
object AutoDownloadPlanner {

	/** Assumed size of one manga chapter until real downloads have been measured. */
	const val DEFAULT_CHAPTER_BYTES = 20L * 1024 * 1024
	const val MIN_CHAPTER_BYTES = 2L * 1024 * 1024
	const val MAX_CHAPTER_BYTES = 100L * 1024 * 1024

	/** Assumed size of one EPUB before it is saved. */
	const val DEFAULT_BOOK_BYTES = 3L * 1024 * 1024

	fun usedBytes(entries: Collection<AutoDownloadEntry>): Long = entries.sumOf { it.bytes.coerceAtLeast(0L) }

	fun remainingBudget(limitBytes: Long, entries: Collection<AutoDownloadEntry>): Long =
		(limitBytes - usedBytes(entries)).coerceAtLeast(0L)

	/** Average chapter size learnt from measured manga entries, clamped to sane bounds. */
	fun estimateChapterBytes(entries: Collection<AutoDownloadEntry>): Long {
		val measured = entries.filter {
			it.kind == AutoDownloadEntry.KIND_MANGA && it.isMeasured && it.chapterIds.isNotEmpty() && it.bytes > 0
		}
		val chapters = measured.sumOf { it.chapterIds.size }
		if (chapters == 0) {
			return DEFAULT_CHAPTER_BYTES
		}
		return (measured.sumOf { it.bytes } / chapters).coerceIn(MIN_CHAPTER_BYTES, MAX_CHAPTER_BYTES)
	}

	/**
	 * The next [count] chapters to read: those after [currentChapterId] when it is in [chapterIds]
	 * (ordered oldest to newest), otherwise from the beginning. Already present chapters are skipped.
	 */
	fun pickChapters(
		chapterIds: List<Long>,
		currentChapterId: Long?,
		excluded: Set<Long>,
		count: Int,
	): List<Long> {
		if (count <= 0 || chapterIds.isEmpty()) {
			return emptyList()
		}
		val currentIndex = if (currentChapterId == null) -1 else chapterIds.indexOf(currentChapterId)
		return chapterIds.asSequence()
			.drop(currentIndex + 1)
			.filterNot { it in excluded }
			.take(count)
			.toList()
	}

	/** How many of [requested] chapters of [chapterBytes] each fit into [remainingBytes]. */
	fun chaptersWithinBudget(requested: Int, chapterBytes: Long, remainingBytes: Long): Int {
		if (requested <= 0 || remainingBytes <= 0L) {
			return 0
		}
		if (chapterBytes <= 0L) {
			return requested
		}
		return minOf(requested.toLong(), remainingBytes / chapterBytes).toInt()
	}

	/** Keeps candidates in their priority order, skipping any that would not fit the remaining budget. */
	fun <T> fitToBudget(candidates: List<T>, budgetBytes: Long, sizeOf: (T) -> Long): List<T> {
		var remaining = budgetBytes
		val result = ArrayList<T>(candidates.size)
		for (candidate in candidates) {
			val size = sizeOf(candidate).coerceAtLeast(0L)
			if (size <= remaining) {
				result.add(candidate)
				remaining -= size
			}
		}
		return result
	}

	/** Never-opened entries first, oldest first; opened entries are never evicted automatically. */
	fun evictionOrder(
		entries: Collection<AutoDownloadEntry>,
		isOpened: (AutoDownloadEntry) -> Boolean,
	): List<AutoDownloadEntry> = entries
		.filterNot(isOpened)
		.sortedBy { it.createdAt }

	/** The smallest prefix of [evictionOrder] that brings usage back under [limitBytes]. */
	fun selectForEviction(
		entries: Collection<AutoDownloadEntry>,
		limitBytes: Long,
		isOpened: (AutoDownloadEntry) -> Boolean,
	): List<AutoDownloadEntry> {
		var used = usedBytes(entries)
		if (used <= limitBytes) {
			return emptyList()
		}
		val result = ArrayList<AutoDownloadEntry>()
		for (entry in evictionOrder(entries, isOpened)) {
			if (used <= limitBytes) {
				break
			}
			result.add(entry)
			used -= entry.bytes.coerceAtLeast(0L)
		}
		return result
	}
}
