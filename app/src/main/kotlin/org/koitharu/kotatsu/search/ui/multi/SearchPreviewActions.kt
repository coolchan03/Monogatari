package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.core.media.MediaType

/** Buttons the search preview can show. Only the valid ones for the selected item are offered. */
enum class PreviewAction { PRIMARY, DETAILS, LIBRARY, DOWNLOAD, CHOOSE_SOURCE, BROWSER, SHARE }

/** What the primary button does for this item. */
enum class PrimaryActionKind { READ, CONTINUE, WATCH, LISTEN, CONTINUE_WATCHING, CONTINUE_LISTENING }

/** Pure rules deciding the preview's action set, so no action silently appears for the wrong media. */
object SearchPreviewActions {

	fun primaryKind(media: MediaType, hasProgress: Boolean): PrimaryActionKind = when (media) {
		MediaType.VIDEO -> if (hasProgress) PrimaryActionKind.CONTINUE_WATCHING else PrimaryActionKind.WATCH
		MediaType.AUDIO -> if (hasProgress) PrimaryActionKind.CONTINUE_LISTENING else PrimaryActionKind.LISTEN
		MediaType.MANGA, MediaType.BOOK -> if (hasProgress) PrimaryActionKind.CONTINUE else PrimaryActionKind.READ
	}

	/**
	 * @param hasAlternatives the same work was found in at least two sources
	 * @param hasWebUrl the item has an http(s) page that can be opened in the browser
	 */
	fun forTitle(media: MediaType, hasAlternatives: Boolean, hasWebUrl: Boolean): List<PreviewAction> = buildList {
		add(PreviewAction.PRIMARY)
		add(PreviewAction.DETAILS)
		add(PreviewAction.LIBRARY)
		// Chapter downloads exist only for image/text chapters, not for streamed video or audio.
		if (media == MediaType.MANGA || media == MediaType.BOOK) add(PreviewAction.DOWNLOAD)
		if (hasAlternatives) add(PreviewAction.CHOOSE_SOURCE)
		if (hasWebUrl) add(PreviewAction.BROWSER)
		add(PreviewAction.SHARE)
	}

	/** Facts line: blank and repeated parts dropped, never filler such as "null" or "Unknown". */
	fun joinFacts(parts: List<String?>): String = parts
		.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
		.distinctBy { it.lowercase() }
		.joinToString(" · ")
}
