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

/** One destination catalogue for the full-screen More hub, in clear groups with Settings first and no duplicates. */
internal object MoreDestinations {
    data class Page(val title: String, val subtitle: String,
        val icon: Int, val target: Class<out Activity>, val viewUri: String? = null,
        val group: String = GROUP_HELP)

    private const val GROUP_SETTINGS = "Settings"
    private const val GROUP_LIBRARY = "Library tools"
    private const val GROUP_SOURCES = "Sources"
    private const val GROUP_FEEDS = "Feeds"
    private const val GROUP_BACKUP = "Backup & sync"
    private const val GROUP_HELP = "Help & about"

    val groupOrder = listOf(GROUP_SETTINGS, GROUP_LIBRARY, GROUP_SOURCES, GROUP_FEEDS, GROUP_BACKUP, GROUP_HELP)

    val pages = listOf(
        Page("Settings", "Appearance, reading, library, sources, backups and privacy",
            R.drawable.ic_settings, SettingsActivity::class.java, group = GROUP_SETTINGS),
        Page("Library", "Manga, books, video and audio; add your own files from its menu",
            R.drawable.ic_monogatari_nav_favorites, UnifiedLibraryActivity::class.java, group = GROUP_LIBRARY),
        Page("Books catalog", "Browse fiction and locally imported ebooks",
            R.drawable.ic_monogatari_nav_books, BookLibraryActivity::class.java, group = GROUP_LIBRARY),
        Page("Downloads", "Saved chapters and offline reading",
            R.drawable.ic_monogatari_nav_local, DownloadsActivity::class.java, group = GROUP_LIBRARY),
        Page("Bookmarks", "Saved pages and reading positions",
            R.drawable.ic_monogatari_nav_bookmarks, AllBookmarksActivity::class.java, group = GROUP_LIBRARY),
        Page("Reading statistics", "Activity, progress and exports",
            R.drawable.ic_monogatari_nav_history, StatsActivity::class.java, group = GROUP_LIBRARY),
        Page("Discover", "Manga, novels, anime, films, audio and news across sources",
            R.drawable.ic_monogatari_nav_explore, DiscoverActivity::class.java, group = GROUP_SOURCES),
        Page("Sources", "Installed websites and extension catalog",
            R.drawable.ic_monogatari_nav_explore, SourcesCatalogActivity::class.java, group = GROUP_SOURCES),
        Page("Extension repositories", "Add repositories and check extension health",
            R.drawable.ic_monogatari_nav_explore, SettingsActivity::class.java,
            "monogatari://source-repos", group = GROUP_SOURCES),
        Page("Source activity log", "See which sources worked or failed, and open a site in the browser",
            R.drawable.ic_monogatari_nav_explore, SourceLogActivity::class.java, group = GROUP_SOURCES),
        Page("API keys & accounts", "Your own keys for Jellyfin, Plex, Twitch and Vimeo, with sign-up links",
            R.drawable.ic_monogatari_nav_local, ApiKeysActivity::class.java, group = GROUP_SOURCES),
        Page("News & followed tags", "RSS feeds and saved interests",
            R.drawable.ic_monogatari_nav_feed, NewsFeedActivity::class.java, group = GROUP_FEEDS),
        Page("Podcasts & video RSS", "Your fiction, film, drama and entertainment subscriptions",
            R.drawable.ic_monogatari_nav_feed, MediaFeedActivity::class.java, group = GROUP_FEEDS),
        Page("Backup & restore", "Create, restore and schedule backups",
            R.drawable.ic_monogatari_nav_local, SettingsActivity::class.java,
            "monogatari://backups", group = GROUP_BACKUP),
        Page("Nearby offline sync", "Pair by code; transfer and merge library or send files",
            R.drawable.ic_monogatari_nav_local, NearbyOfflineSyncActivity::class.java, group = GROUP_BACKUP),
        Page("Manual WebDAV backups", "Explicit ZIP transfers; no auto-restore",
            R.drawable.ic_monogatari_nav_local, WebDavManualSyncActivity::class.java, group = GROUP_BACKUP),
        Page("Import from Kotatsu / Mangayomi", "Bring over your library, categories and reading progress",
            R.drawable.ic_monogatari_nav_local, ForeignBackupReviewActivity::class.java, group = GROUP_BACKUP),
        Page("Source test center", "Run diagnostics and copy reports",
            R.drawable.ic_monogatari_nav_explore, MonogatariTestCenterActivity::class.java),
        Page("Open-source licences", "Offline credits, full GPL and upstream notices",
            R.drawable.ic_monogatari_nav_books, OpenSourceLicensesActivity::class.java),
        Page("About", "Version, credits and app information",
            R.drawable.ic_monogatari_nav_books, SettingsActivity::class.java, "monogatari://about"),
    )

    /** Section titles (String) interleaved with their destinations (Page). */
    val sections: List<Any> = buildList {
        for (group in groupOrder) {
            val members = pages.filter { it.group == group }
            if (members.isEmpty()) continue
            add(group)
            addAll(members)
        }
    }

    fun show(activity: MainActivity) =
        activity.startActivity(Intent(activity, MoreActivity::class.java))
}
