package org.koitharu.kotatsu.main.ui

import org.koitharu.kotatsu.discover.DiscoverActivity
import org.koitharu.kotatsu.discover.media.MediaDiscoverActivity
import android.app.Activity
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
    /** Hub groups in display order; [id] is stable, the heading text is a string resource. */
    enum class Group(val id: String, @StringRes val title: Int) {
        SETTINGS("settings", R.string.more_group_settings),
        LIBRARY("library", R.string.more_group_library),
        SOURCES("sources", R.string.more_group_sources),
        FEEDS("feeds", R.string.more_group_feeds),
        BACKUP("backup", R.string.more_group_backup),
        HELP("help", R.string.more_group_help),
    }

    /** [id] is stable; [media] is the accent kind name (BOOK, VIDEO, NEWS, AUDIO) or null for the default. */
    data class Page(val id: String, @StringRes val title: Int, @StringRes val subtitle: Int,
        @DrawableRes val icon: Int, val target: Class<out Activity>, val group: Group,
        val viewUri: String? = null, val media: String? = null)

    val groupOrder = Group.entries

    val pages = listOf(
        Page("settings", R.string.more_page_settings, R.string.more_page_settings_summary,
            R.drawable.ic_settings, SettingsActivity::class.java, Group.SETTINGS),
        Page("library", R.string.more_page_library, R.string.more_page_library_summary,
            R.drawable.ic_monogatari_nav_favorites, UnifiedLibraryActivity::class.java, Group.LIBRARY),
        Page("books_catalog", R.string.more_page_books_catalog, R.string.more_page_books_catalog_summary,
            R.drawable.ic_monogatari_nav_books, BookLibraryActivity::class.java, Group.LIBRARY, media = "BOOK"),
        Page("downloads", R.string.more_page_downloads, R.string.more_page_downloads_summary,
            R.drawable.ic_monogatari_nav_local, DownloadsActivity::class.java, Group.LIBRARY),
        Page("bookmarks", R.string.more_page_bookmarks, R.string.more_page_bookmarks_summary,
            R.drawable.ic_monogatari_nav_bookmarks, AllBookmarksActivity::class.java, Group.LIBRARY, media = "BOOK"),
        Page("stats", R.string.more_page_stats, R.string.more_page_stats_summary,
            R.drawable.ic_monogatari_nav_history, StatsActivity::class.java, Group.LIBRARY),
        Page("discover", R.string.more_page_discover, R.string.more_page_discover_summary,
            R.drawable.ic_monogatari_nav_explore, DiscoverActivity::class.java, Group.SOURCES, media = "VIDEO"),
        Page("discover_books", R.string.more_page_discover_books, R.string.more_page_discover_books_summary,
            R.drawable.ic_monogatari_nav_books, MediaDiscoverActivity::class.java, Group.SOURCES, viewUri = "monogatari://discover/books", media = "BOOK"),
        Page("discover_video", R.string.more_page_discover_video, R.string.more_page_discover_video_summary,
            R.drawable.ic_play, MediaDiscoverActivity::class.java, Group.SOURCES, viewUri = "monogatari://discover/video", media = "VIDEO"),
        Page("sources", R.string.more_page_sources, R.string.more_page_sources_summary,
            R.drawable.ic_monogatari_nav_explore, SourcesCatalogActivity::class.java, Group.SOURCES),
        Page("repos", R.string.more_page_repos, R.string.more_page_repos_summary,
            R.drawable.ic_monogatari_nav_explore, SettingsActivity::class.java, Group.SOURCES, viewUri = "monogatari://source-repos"),
        Page("source_log", R.string.more_page_source_log, R.string.more_page_source_log_summary,
            R.drawable.ic_monogatari_nav_explore, SourceLogActivity::class.java, Group.SOURCES),
        Page("api_keys", R.string.more_page_api_keys, R.string.more_page_api_keys_summary,
            R.drawable.ic_monogatari_nav_local, ApiKeysActivity::class.java, Group.SOURCES),
        Page("news", R.string.more_page_news, R.string.more_page_news_summary,
            R.drawable.ic_monogatari_nav_feed, NewsFeedActivity::class.java, Group.FEEDS, media = "NEWS"),
        Page("media_feeds", R.string.more_page_media_feeds, R.string.more_page_media_feeds_summary,
            R.drawable.ic_monogatari_nav_feed, MediaFeedActivity::class.java, Group.FEEDS, media = "VIDEO"),
        Page("backup", R.string.more_page_backup, R.string.more_page_backup_summary,
            R.drawable.ic_monogatari_nav_local, SettingsActivity::class.java, Group.BACKUP, viewUri = "monogatari://backups"),
        Page("nearby_sync", R.string.more_page_nearby_sync, R.string.more_page_nearby_sync_summary,
            R.drawable.ic_monogatari_nav_local, NearbyOfflineSyncActivity::class.java, Group.BACKUP),
        Page("webdav", R.string.more_page_webdav, R.string.more_page_webdav_summary,
            R.drawable.ic_monogatari_nav_local, WebDavManualSyncActivity::class.java, Group.BACKUP),
        Page("import", R.string.more_page_import, R.string.more_page_import_summary,
            R.drawable.ic_monogatari_nav_local, ForeignBackupReviewActivity::class.java, Group.BACKUP),
        Page("test_center", R.string.more_page_test_center, R.string.more_page_test_center_summary,
            R.drawable.ic_monogatari_nav_explore, MonogatariTestCenterActivity::class.java, Group.HELP),
        Page("licences", R.string.more_page_licences, R.string.more_page_licences_summary,
            R.drawable.ic_monogatari_nav_books, OpenSourceLicensesActivity::class.java, Group.HELP),
        Page("about", R.string.more_page_about, R.string.more_page_about_summary,
            R.drawable.ic_monogatari_nav_books, SettingsActivity::class.java, Group.HELP, viewUri = "monogatari://about"),
    )

    /** Group headings (Group) interleaved with their destinations (Page). */
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
