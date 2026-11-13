package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.parsers.model.Manga

/** What the search preview pane shows for the selected title; only facts the source or the user's data provide. */
data class SearchPreviewState(
	val manga: Manga,
	/** True while the full details are being requested for this selection. */
	val loading: Boolean = false,
	/** True when the details request failed, so only the search result's own fields are shown. */
	val detailsFailed: Boolean = false,
	/** Reading progress 0..1 from local history, or null when the title was never opened. */
	val progress: Float? = null,
	/** Zero-based index of the chapter in progress, or -1. */
	val currentChapter: Int = -1,
	val totalChapters: Int = 0,
	val inLibrary: Boolean = false,
)

/** Result of one lazy details request. */
data class SearchPreviewDetails(
	val manga: Manga,
	val detailsLoaded: Boolean,
	val progress: Float?,
	val currentChapter: Int,
	val totalChapters: Int,
	val inLibrary: Boolean,
)
