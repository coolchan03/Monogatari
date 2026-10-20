package org.koitharu.kotatsu.core.prefs

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import org.koitharu.kotatsu.R

@Keep
enum class NavItem(
	@IdRes val id: Int,
	@StringRes val title: Int,
	@DrawableRes val icon: Int,
) {

	BOOKS(R.id.nav_books, R.string.monogatari_books, R.drawable.ic_monogatari_nav_books),
	MORE(R.id.nav_more, R.string.monogatari_nav_more, R.drawable.ic_monogatari_nav_more),
	DISCOVER(R.id.nav_discover, R.string.discover, R.drawable.ic_star_rate_outline),
	HISTORY(R.id.nav_history, R.string.history, R.drawable.ic_monogatari_nav_history),
	FAVORITES(R.id.nav_favorites, R.string.favourites, R.drawable.ic_monogatari_nav_favorites),
	LOCAL(R.id.nav_local, R.string.on_device, R.drawable.ic_monogatari_nav_local),
	EXPLORE(R.id.nav_explore, R.string.explore, R.drawable.ic_monogatari_nav_explore),
	SUGGESTIONS(R.id.nav_suggestions, R.string.suggestions, R.drawable.ic_monogatari_nav_suggestions),
	FEED(R.id.nav_feed, R.string.feed, R.drawable.ic_monogatari_nav_feed),
	UPDATED(R.id.nav_updated, R.string.updated, R.drawable.ic_monogatari_nav_updated),
	BOOKMARKS(R.id.nav_bookmarks, R.string.bookmarks, R.drawable.ic_monogatari_nav_bookmarks),
	DISCOVER_BOOKS(R.id.nav_discover_books, R.string.media_discover_books_nav, R.drawable.ic_monogatari_nav_books),
	DISCOVER_VIDEO(R.id.nav_discover_video, R.string.media_discover_video_nav, R.drawable.ic_play),
	;

	fun isAvailable(settings: AppSettings): Boolean = when (this) {
		SUGGESTIONS -> settings.isSuggestionsEnabled
		UPDATED, FEED -> settings.isTrackerEnabled
		else -> true
	}
}
