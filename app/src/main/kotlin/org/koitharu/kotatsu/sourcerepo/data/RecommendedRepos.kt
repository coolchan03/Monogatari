package org.koitharu.kotatsu.sourcerepo.data

import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.sourcerepo.domain.normalizeRepoUrl

/**
 * Mangayomi-format repositories offered to every install (see docs/SOURCE_REPOS.md).
 * Keiyoushi/Secozzi Mihon/Aniyomi APK indexes are intentionally absent: they cannot run here.
 */
object RecommendedRepos {

	val manga: List<String> = listOf(
		"https://raw.githubusercontent.com/kodjodevf/mangayomi-extensions/refs/heads/main/index.json",
		// The raw m2k3a index.json is not listed, so this identical GitHub Pages mirror is the only copy.
		"https://m2k3a.github.io/mangayomi-extensions/index.json",
		"https://raw.githubusercontent.com/Swakshan/mangayomi-swak-extensions/refs/heads/main/index.json",
		"https://raw.githubusercontent.com/Schnitzel5/sugoi-modules/refs/heads/main/index.json",
	)

	val anime: List<String> = listOf(
		"https://raw.githubusercontent.com/m2k3a/mangayomi-extensions/refs/heads/main/anime_index.json",
		"https://raw.githubusercontent.com/Swakshan/mangayomi-swak-extensions/refs/heads/main/anime_index.json",
		"https://raw.githubusercontent.com/Schnitzel5/sugoi-modules/refs/heads/main/anime_index.json",
		"https://raw.githubusercontent.com/gato404/kegareta-sauces/refs/heads/main/anime_index.json",
	)

	// m2k3a.github.io/novel_index.json is byte-identical to the raw m2k3a file below, so it is omitted.
	val novel: List<String> = listOf(
		"https://raw.githubusercontent.com/Schnitzel5/sugoi-modules/refs/heads/main/novel_index.json",
		"https://raw.githubusercontent.com/m2k3a/mangayomi-extensions/refs/heads/main/novel_index.json",
		"https://raw.githubusercontent.com/kodjodevf/mangayomi-extensions/refs/heads/main/novel_index.json",
		"https://raw.githubusercontent.com/Swakshan/mangayomi-swak-extensions/refs/heads/main/novel_index.json",
	)

	val byType: Map<JsItemType, List<String>> = mapOf(
		JsItemType.MANGA to manga,
		JsItemType.ANIME to anime,
		JsItemType.NOVEL to novel,
	)

	val all: List<String> = manga + anime + novel

	/** Recommended repos plus the build-time list (comma separated), valid https URLs only. */
	fun mergedDefaults(buildTimeList: String): Set<String> =
		(parseList(buildTimeList) + all.mapNotNull { normalizeRepoUrl(it) }).toSet()

	fun parseList(list: String): Set<String> =
		list.split(',').map { it.trim() }.mapNotNull { normalizeRepoUrl(it) }.toSet()
}

/** Pure seeding logic of [SourceRepoStore]; kept separate so it can be unit-tested. */
object RepoSeeding {

	data class Result(val urls: Set<String>, val seeded: Set<String>)

	/**
	 * @param stored repos the user has (null when nothing was ever stored: a fresh install)
	 * @param seeded default URLs already offered once (null before this feature existed)
	 * @param defaults every default URL
	 * @param legacyDefaults defaults an install without [seeded] was already offered at first launch
	 * A removed repo stays removed because [seeded] only ever grows.
	 */
	fun seed(stored: Set<String>?, seeded: Set<String>?, defaults: Set<String>, legacyDefaults: Set<String>): Result {
		val offered = seeded ?: if (stored == null) emptySet() else legacyDefaults
		return Result(
			urls = (stored ?: emptySet()) + (defaults - offered),
			seeded = offered + defaults,
		)
	}
}
