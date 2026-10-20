package org.koitharu.kotatsu.discover.media

import androidx.annotation.StringRes
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.discover.AniListDiscover

/**
 * One horizontal row on a media Discover page. Exactly one of [source], [feed] or [airing] says where its titles
 * come from: a built-in source's no-query listing, an AniList list, or AniList's airing schedule for the week.
 */
data class MediaDiscoverShelf(
	val key: String,
	@StringRes val title: Int,
	val source: BuiltinSource? = null,
	val feed: AniListDiscover.Feed? = null,
	val airing: Boolean = false,
)

sealed interface MediaDiscoverShelfState {

	data object Loading : MediaDiscoverShelfState

	data class Loaded(val items: List<AniListDiscover.Item>) : MediaDiscoverShelfState

	/** Failed or came back empty: the row is not shown at all. */
	data object Hidden : MediaDiscoverShelfState
}

data class MediaDiscoverShelfModel(
	val shelf: MediaDiscoverShelf,
	val state: MediaDiscoverShelfState,
)

object MediaDiscoverShelves {

	private fun source(source: BuiltinSource, @StringRes title: Int) = MediaDiscoverShelf(source.name, title, source = source)

	private fun feed(feed: AniListDiscover.Feed, @StringRes title: Int) = MediaDiscoverShelf("anilist:${feed.name}", title, feed = feed)

	fun forType(type: MediaType): List<MediaDiscoverShelf> = when (type) {
		MediaType.VIDEO -> listOf(
			source(BuiltinSource.YOUTUBE, R.string.media_discover_shelf_youtube),
			MediaDiscoverShelf("anilist:airing", R.string.media_discover_shelf_airing, airing = true),
			feed(AniListDiscover.Feed.TRENDING_ANIME, R.string.media_discover_shelf_trending_anime),
			feed(AniListDiscover.Feed.BL_ANIME, R.string.media_discover_shelf_bl_anime),
			source(BuiltinSource.DAILYMOTION, R.string.media_discover_shelf_dailymotion),
			source(BuiltinSource.ODYSEE, R.string.media_discover_shelf_odysee),
			source(BuiltinSource.IA_VIDEO, R.string.media_discover_shelf_ia_video),
		)

		MediaType.BOOK -> listOf(
			feed(AniListDiscover.Feed.LIGHT_NOVELS, R.string.media_discover_shelf_light_novels),
			source(BuiltinSource.GUTENBERG, R.string.media_discover_shelf_gutenberg),
			source(BuiltinSource.ROYAL_ROAD, R.string.media_discover_shelf_royal_road),
			source(BuiltinSource.AO3, R.string.media_discover_shelf_ao3),
			source(BuiltinSource.SCRIBBLE_HUB, R.string.media_discover_shelf_scribble_hub),
			source(BuiltinSource.WIKISOURCE, R.string.media_discover_shelf_wikisource),
			source(BuiltinSource.OPEN_LIBRARY, R.string.media_discover_shelf_open_library),
			source(BuiltinSource.IA_BOOKS, R.string.media_discover_shelf_ia_books),
		)

		else -> emptyList()
	}
}
