package org.koitharu.kotatsu.main.ui

import org.koitharu.kotatsu.discover.DiscoverActivity
import android.app.Activity
import android.content.Intent
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.WebDavManualSyncActivity
import org.koitharu.kotatsu.backups.domain.NearbyOfflineSyncActivity
import org.koitharu.kotatsu.backups.domain.ForeignBackupReviewActivity
import org.koitharu.kotatsu.bookmarks.ui.AllBookmarksActivity
import org.koitharu.kotatsu.download.ui.list.DownloadsActivity
import org.koitharu.kotatsu.settings.sources.catalog.SourcesCatalogActivity
import org.koitharu.kotatsu.core.keys.ApiKeysActivity
import org.koitharu.kotatsu.sourcehealth.SourceLogActivity
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
        Page("API keys & accounts", "Your own keys for Jellyfin, Plex, Twitch and Vimeo, with sign-up links",
            R.drawable.ic_monogatari_nav_local, ApiKeysActivity::class.java),
        Page("Source activity log", "See which sources worked or failed, and open a site in the browser",
            R.drawable.ic_monogatari_nav_explore, SourceLogActivity::class.java),
        Page("Nearby offline sync", "Pair by code; transfer and merge library or send files",
            R.drawable.ic_monogatari_nav_local, NearbyOfflineSyncActivity::class.java),
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

    /** Settings-style section titles, in display order, with the destinations that belong to each. */
    private val groupOf = mapOf(
        "Unified Library" to "Library", "Books catalog" to "Library", "Downloads" to "Library",
        "Bookmarks" to "Library", "Reading statistics" to "Library",
        "Discover" to "Find something", "Sources" to "Find something", "Podcasts & video RSS" to "Find something",
        "News & followed tags" to "Find something", "API keys & accounts" to "Find something",
        "Source activity log" to "Find something",
        "Nearby offline sync" to "Backup and import",
        "Manual WebDAV backups" to "Backup and import", "Import from Kotatsu / Mangayomi" to "Backup and import",
    )
    private const val DEFAULT_GROUP = "App and tools"
    val groupOrder = listOf("Library", "Find something", "Backup and import", DEFAULT_GROUP)

    /** Section titles (String) interleaved with their destinations (Page). */
    val sections: List<Any> = buildList {
        for (group in groupOrder) {
            val members = pages.filter { (groupOf[it.title] ?: DEFAULT_GROUP) == group }
            if (members.isEmpty()) continue
            add(group)
            addAll(members)
        }
    }

    fun show(activity: MainActivity) =
        activity.startActivity(Intent(activity, MoreActivity::class.java))
}
