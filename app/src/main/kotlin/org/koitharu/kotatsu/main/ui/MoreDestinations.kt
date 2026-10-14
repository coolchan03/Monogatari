package org.koitharu.kotatsu.main.ui

import org.koitharu.kotatsu.discover.DiscoverActivity
import android.app.Activity
import android.content.Intent
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.WebDavManualSyncActivity
import org.koitharu.kotatsu.backups.domain.ForeignBackupReviewActivity
import org.koitharu.kotatsu.bookmarks.ui.AllBookmarksActivity
import org.koitharu.kotatsu.download.ui.list.DownloadsActivity
import org.koitharu.kotatsu.settings.sources.catalog.SourcesCatalogActivity
import org.koitharu.kotatsu.builtin.jellyfin.JellyfinSetupActivity
import org.koitharu.kotatsu.core.media.BookLibraryActivity
import org.koitharu.kotatsu.core.media.MediaFeedActivity
import org.koitharu.kotatsu.core.media.UnifiedLibraryActivity
import org.koitharu.kotatsu.core.qa.MonogatariTestCenterActivity
import org.koitharu.kotatsu.core.qa.NewsFeedActivity
import org.koitharu.kotatsu.settings.SettingsActivity
import org.koitharu.kotatsu.stats.ui.StatsActivity

/** One destination catalogue for the full-screen More hub. */
internal object MoreDestinations {
    data class Page(val title: String, val subtitle: String,
        val icon: Int, val target: Class<out Activity>, val viewUri: String? = null)
    val pages = listOf(
        Page("Unified Library", "Manga, books, video and audio",
            R.drawable.ic_monogatari_nav_favorites, UnifiedLibraryActivity::class.java),
        Page("Books catalog", "Browse fiction and locally imported ebooks",
            R.drawable.ic_monogatari_nav_books, BookLibraryActivity::class.java),
        Page("Downloads", "Saved chapters and offline reading",
            R.drawable.ic_monogatari_nav_local, DownloadsActivity::class.java),
        Page("Reading statistics", "Activity, progress and exports",
            R.drawable.ic_monogatari_nav_history, StatsActivity::class.java),
        Page("Bookmarks", "Saved pages and reading positions",
            R.drawable.ic_monogatari_nav_bookmarks, AllBookmarksActivity::class.java),
        Page("Sources", "Installed websites and extension catalog",
            R.drawable.ic_monogatari_nav_explore, SourcesCatalogActivity::class.java),
        Page("Discover", "Manga, novels, anime, films and audio across sources",
            R.drawable.ic_monogatari_nav_explore, DiscoverActivity::class.java),
        Page("Podcasts & video RSS", "Your fiction, film, drama and entertainment subscriptions",
            R.drawable.ic_monogatari_nav_feed, MediaFeedActivity::class.java),
        Page("News & followed tags", "RSS feeds and saved interests",
            R.drawable.ic_monogatari_nav_feed, NewsFeedActivity::class.java),
        Page("Jellyfin server", "Connect your own Jellyfin library with its address and API key",
            R.drawable.ic_monogatari_nav_local, JellyfinSetupActivity::class.java),
        Page("Manual WebDAV backups", "Explicit ZIP transfers; no auto-restore",
            R.drawable.ic_monogatari_nav_local, WebDavManualSyncActivity::class.java),
        Page("Import from Kotatsu / Mangayomi", "Bring over your library, categories and reading progress",
            R.drawable.ic_monogatari_nav_local, ForeignBackupReviewActivity::class.java),
        Page("Source test center", "Run diagnostics and copy reports",
            R.drawable.ic_monogatari_nav_explore, MonogatariTestCenterActivity::class.java),
        Page("Open-source licences", "Offline credits, full GPL and upstream notices",
            R.drawable.ic_monogatari_nav_books, OpenSourceLicensesActivity::class.java),
        Page("Settings", "Appearance, readers, sources and privacy",
            R.drawable.ic_monogatari_nav_bookmarks, SettingsActivity::class.java),
        Page("About", "Version, credits and app information",
            R.drawable.ic_monogatari_nav_books, SettingsActivity::class.java, "monogatari://about"),
    )
    fun show(activity: MainActivity) =
        activity.startActivity(Intent(activity, MoreActivity::class.java))
}
