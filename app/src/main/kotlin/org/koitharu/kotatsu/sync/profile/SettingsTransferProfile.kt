package org.koitharu.kotatsu.sync.profile

/**
 * Draft (v1.1) choice of which settings travel to another device (Nearby transfer or restore).
 *
 * Keys are grouped by meaning; device-specific and secret values (app lock, proxy credentials, tokens, storage
 * folders) are never transferred whatever the user picks.
 */
object SettingsTransferProfile {

	enum class Group { APPEARANCE, READER, LIBRARY, SOURCES, NETWORK, TRACKING, OTHER }

	/** Never leaves the device: secrets, per-device paths and one-time migration flags. */
	private val DEVICE_ONLY = listOf(
		"app_password", "protect_app", "proxy_login", "proxy_password", "discord_token", "backup_folder_sync",
		"backup_periodic_output", "backup_periodic_last", "backup_periodic_tg_chat_id", "local_manga_dirs",
		"local_storage", "pages_dir", "ssl_bypass", "app_version", "tracker_unstuck_migration", "tracker_progress_refresh",
	)

	private val PREFIXES = listOf(
		Group.APPEARANCE to listOf("theme", "color_theme", "monogatari_accent", "amoled", "grid_size", "list_mode", "app_locale",
			"nav_", "floating_nav", "main_fab", "enhanced_colors", "manga_list_badges", "progress_indicators", "feed_header"),
		Group.READER to listOf("reader_", "webtoon_", "zoom_mode", "pages_numbers", "pages_preload", "eink_", "cf_", "as_",
			"screenshots_policy"),
		Group.LIBRARY to listOf("history_", "fav_", "local_order", "all_favourites_visible", "updated_grouping",
			"reverse_chapters", "grid_view_chapters", "description_collapse", "details_", "pages_tab", "quick_filter"),
		Group.SOURCES to listOf("sources_", "remote_sources", "monogatari_", "no_nsfw", "active_source_preset",
			"media_type_filter", "search_suggest_types", "related_manga", "suggestions"),
		Group.NETWORK to listOf("doh", "proxy", "images_proxy", "mirror_switching", "downloads_", "prefetch_content",
			"tracker_wifi", "suggestions_wifi"),
		Group.TRACKING to listOf("tracker_", "track_", "sync_period", "notifications_", "shikimori", "anilist", "mal", "kitsu",
			"stats_on", "reading_time"),
	)

	fun isDeviceOnly(key: String): Boolean = DEVICE_ONLY.any { key == it || key.startsWith(it) }

	fun groupOf(key: String): Group {
		// Network-ish tracker keys are listed under NETWORK, so check the more specific groups in declaration order
		// but let an exact network key win over a broader prefix.
		PREFIXES.firstOrNull { (group, prefixes) -> group == Group.NETWORK && prefixes.any { key == it || key.startsWith(it) } }
			?.let { return it.first }
		return PREFIXES.firstOrNull { (_, prefixes) -> prefixes.any { key == it || key.startsWith(it) } }?.first ?: Group.OTHER
	}

	/** The subset of [settings] to send or apply for the chosen [groups]. */
	fun <V> filter(settings: Map<String, V>, groups: Set<Group>): Map<String, V> =
		settings.filterKeys { key -> !isDeviceOnly(key) && groupOf(key) in groups }
}
