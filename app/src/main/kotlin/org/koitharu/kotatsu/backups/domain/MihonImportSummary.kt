package org.koitharu.kotatsu.backups.domain

import java.util.Locale

/**
 * What a Mihon/Tachiyomi backup would bring over. Tachiyomi source ids are extension-specific and not portable, so
 * sources are matched to Monogatari sources by their unique display name only; everything else needs migration.
 */
internal data class MihonImportSummary(
	val titles: Int,
	val favourites: Int,
	val withProgress: Int,
	val categories: Int,
	val matchedSources: Map<String, String>,
	val unmatchedSources: List<String>,
	val matchedTitles: Int,
) {
	companion object {

		/** @param available Monogatari source display name -> source key (parser name or `js:<id>`). */
		fun of(backup: MihonBackup.Backup, available: Map<String, String>): MihonImportSummary {
			val byName = available.entries.groupBy { normalize(it.key) }
				.filterValues { it.size == 1 }
				.mapValues { it.value.single().value }
			val usedSourceNames = backup.manga.map { backup.sources[it.sourceId] ?: "Source ${it.sourceId}" }.distinct()
			val matched = LinkedHashMap<String, String>()
			val unmatched = ArrayList<String>()
			for (name in usedSourceNames) {
				val key = byName[normalize(name)]
				if (key != null) matched[name] = key else unmatched += name
			}
			return MihonImportSummary(
				titles = backup.manga.size,
				favourites = backup.manga.count { it.favorite },
				withProgress = backup.manga.count { m -> m.chapters.any { it.read || it.lastPageRead > 0 } || m.history.isNotEmpty() },
				categories = backup.categories.size,
				matchedSources = matched,
				unmatchedSources = unmatched.sorted(),
				matchedTitles = backup.manga.count { (backup.sources[it.sourceId] ?: "") in matched },
			)
		}

		/** Mihon names often carry a language suffix ("MangaDex (EN)"); compare without it. */
		internal fun normalize(name: String): String = name.trim()
			.replace(Regex("\\s*\\((?:[a-z]{2,3}(?:-[a-z]{2,4})?|all|multi)\\)$", RegexOption.IGNORE_CASE), "")
			.lowercase(Locale.ROOT)
			.replace(Regex("[^\\p{L}\\p{N}]+"), "")
	}
}
