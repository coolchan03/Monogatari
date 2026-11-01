package org.koitharu.kotatsu.search.ui.suggestion

import org.koitharu.kotatsu.core.media.MediaType

/** Which hint the main search bar shows; mapped to a string resource by the activity. */
enum class SearchHint {
	FILTER_HISTORY,
	FILTER_FAVOURITES,
	SEARCH_ALL,
	SEARCH_MANGA,
	SEARCH_BOOKS,
	SEARCH_VIDEO,
	SEARCH_AUDIO,
}

/** Pure rules for the shared search media type and the main bar's hint (no Android dependencies). */
object SearchScopeRules {

	const val ALL = "ALL"

	val scopes: List<String> = listOf(ALL) + MediaType.entries.map { it.name }

	/** The scope itself when it is "ALL" or a [MediaType] name, otherwise null. */
	fun normalize(raw: String?): String? = raw?.takeIf { it in scopes }

	/** Never null: a valid scope, else "ALL". */
	fun orAll(raw: String?): String = normalize(raw) ?: ALL

	/**
	 * The search scope a Discover/Library chip implies: its name when it is a media type, otherwise "ALL"
	 * (the "ALL" chip, News, which is not a source type, and anything unknown).
	 */
	fun scopeForSection(sectionName: String?): String = when (sectionName) {
		MediaType.MANGA.name, MediaType.BOOK.name, MediaType.VIDEO.name, MediaType.AUDIO.name -> sectionName.orEmpty()
		else -> ALL
	}

	/**
	 * @param isFiltering true only when the bar filters the History/Favourites screen instead of searching sources
	 */
	fun hintFor(screen: SearchSuggestionScope, isFiltering: Boolean, scope: String?): SearchHint = when {
		isFiltering && screen == SearchSuggestionScope.HISTORY -> SearchHint.FILTER_HISTORY
		isFiltering && screen == SearchSuggestionScope.FAVOURITES -> SearchHint.FILTER_FAVOURITES
		else -> when (orAll(scope)) {
			MediaType.MANGA.name -> SearchHint.SEARCH_MANGA
			MediaType.BOOK.name -> SearchHint.SEARCH_BOOKS
			MediaType.VIDEO.name -> SearchHint.SEARCH_VIDEO
			MediaType.AUDIO.name -> SearchHint.SEARCH_AUDIO
			else -> SearchHint.SEARCH_ALL
		}
	}
}
