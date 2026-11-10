package org.koitharu.kotatsu.reader.domain

import org.koitharu.kotatsu.parsers.model.MangaChapter
import kotlin.math.abs

/**
 * Chooses the chapter reached by "previous"/"next" from the current one.
 *
 * Default (simple) behaviour: stay within the current branch (scanlator group) so a title with
 * several groups never hops to another group's upload, and stop at the end of that branch.
 */
object ChapterNavigator {

	data class Options(
		/** Navigate inside the current branch only. */
		val preferSameBranch: Boolean = true,
		/** Skip chapters for which the `isRead` callback returns true. */
		val skipRead: Boolean = false,
		/** Skip further uploads with the same volume and number as the chapter we are leaving. */
		val skipDuplicateNumbers: Boolean = false,
	)

	/**
	 * @param all every chapter of the title in reading order (all branches)
	 * @param delta steps to move; negative = previous; 0 returns the current chapter
	 * @return the target chapter, or null when the current chapter is unknown or there is nothing further
	 */
	fun target(
		all: List<MangaChapter>,
		currentId: Long,
		delta: Int,
		options: Options = Options(),
		isRead: (MangaChapter) -> Boolean = { false },
	): MangaChapter? {
		val current = all.firstOrNull { it.id == currentId } ?: return null
		if (delta == 0) return current
		val pool = if (options.preferSameBranch) all.filter { it.branch == current.branch } else all
		var index = pool.indexOfFirst { it.id == currentId }
		if (index < 0) return null
		val step = if (delta > 0) 1 else -1
		var landed = current
		repeat(abs(delta)) {
			var candidate = index + step
			while (candidate in pool.indices) {
				val c = pool[candidate]
				val skip = (options.skipRead && isRead(c)) ||
					(options.skipDuplicateNumbers && c.number > 0f && sameNumber(c, landed))
				if (!skip) break
				candidate += step
			}
			if (candidate !in pool.indices) return null
			index = candidate
			landed = pool[candidate]
		}
		return landed
	}

	private fun sameNumber(a: MangaChapter, b: MangaChapter) = a.number == b.number && a.volume == b.volume
}
