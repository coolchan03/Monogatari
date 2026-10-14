package org.koitharu.kotatsu.core.qa

import org.koitharu.kotatsu.R

/** Success for a parser network request is not proof that a chapter or video can actually play. */
enum class CheckResult(val label: String) {
    UNTESTED("Not tested"), PASS("Looks good"), FAIL("Needs fixing"),
}

/** Aesthetic/product feedback is separate from the functional pass/fail verdict. */
enum class ExperienceRating(val label: String) {
    UNRATED("No opinion yet"), LOVE("Love it"), GOOD("Like it"), MIXED("It's OK"),
    NEEDS_WORK("Needs work"), NOT_FOR_ME("Not for me"),
}

enum class TestDestination {
    HOME, MANGA_SEARCH, BOOKS, BOOK_SEARCH, ANIME_SEARCH, AUDIO_SEARCH, SOURCES, SETTINGS, DOWNLOADS,
    LIBRARY, UNIFIED_LIBRARY, DISCOVER, NEWS, MEDIA_FEEDS, IMPORT, NONE,
}

data class TestCase(
    val id: String,
    val title: String,
    val instructions: String,
    val destination: TestDestination,
    /** Views circled by [TestSpotlight] when they are on screen. */
    val highlight: List<Int> = emptyList(),
    /** Also circle the toolbar's ⋮ menu button. */
    val highlightOverflow: Boolean = false,
)

data class TestFinding(val result: CheckResult, val notes: String, val feeling: ExperienceRating = ExperienceRating.UNRATED)

object MonogatariTestPlan {
    val cases = listOf(
        TestCase("startup", "Launch and device checks",
            "Does the app start without crashing? Check its permissions, network status and storage.",
            TestDestination.NONE),
        TestCase("visual", "Interface and navigation",
            "Check Home, Explore, Books, bottom navigation, settings and the mascot-free design. Look for clipping or old branding.",
            TestDestination.HOME),
        TestCase("manga_search", "Manga global search",
            "Search a manga by title, try several sources, and check that broken/empty sources do not overwhelm results.",
            TestDestination.MANGA_SEARCH),
        TestCase("manga_filters", "Filters and publication year",
            "Test year, source selection and other filters, and verify result cards match your selection.",
            TestDestination.MANGA_SEARCH),
        TestCase("captcha", "CAPTCHA and protected websites",
            "Try a source asking for verification. Does CAPTCHA/login work and return to manga?",
            TestDestination.SOURCES),
        TestCase("manga_reader", "Manga reading and progress",
            "Read a chapter, scroll pages, change reading settings, close and reopen it. Is your position remembered?",
            TestDestination.HOME),
        TestCase("favourites", "Library, favourites and history",
            "Save manga, put it in a collection, and reopen. Check favourites, history and updates.",
            TestDestination.HOME),
        TestCase("book_sources", "Novel and book-source search",
            "Search a novel through installed sources. Confirm manga-style results and readable chapters.",
            TestDestination.BOOK_SEARCH),
        TestCase("public_catalogs", "Gutenberg, Open Library, Archive and OPDS",
            "Search a known title, open public catalog results and an OPDS feed. Check each actual link.",
            TestDestination.BOOK_SEARCH),
        TestCase("local_books", "EPUB, PDF, HTML and text",
            "Import a local book in every available format. Open it and check reading progress.",
            TestDestination.BOOKS),
        TestCase("anime_search", "Anime and video source search",
            "Search an anime, open episode details, and check hosts and streams.",
            TestDestination.ANIME_SEARCH),
        TestCase("video_playback", "Actual video playback",
            "Play, seek, pause and rotate a video. Test subtitles if offered. Stream listings alone are not a pass.",
            TestDestination.ANIME_SEARCH),
        TestCase("librivox_audio", "LibriVox background audio",
            "Search LibriVox, play an audiobook, turn the screen off, use lock-screen play/pause, then reopen the reader. Is the position restored and the notification correct?",
            TestDestination.AUDIO_SEARCH),
        TestCase("opds_navigation", "Saved OPDS catalog navigation",
            "Add an OPDS feed with nested sections. Find an EPUB, open it and test progress. Check that off-host navigation does not load unexpectedly.",
            TestDestination.BOOK_SEARCH),
        TestCase("youtube_extractor", "YouTube extraction and playback",
            "Search BL drama on the YouTube source, open a video and confirm picture plus sound. Note age restrictions, captions, and loading errors.",
            TestDestination.ANIME_SEARCH),
        TestCase("video_mode_filters", "Video, channel and playlist search modes",
            "From Video search, open the search-type menu. Select Videos, Channels, then Playlists; search for the same phrase each time. Confirm the YouTube results follow the selection and other sources still work.",
            TestDestination.ANIME_SEARCH),
        TestCase("video_collections", "Channel and playlist continuation pages",
            "Open a large YouTube channel and a long playlist. Verify more than the first page loads, videos are not duplicated, and chapters play in the expected order.",
            TestDestination.ANIME_SEARCH),
        TestCase("channel_updates", "New videos from followed channels",
            "Add a YouTube channel to your library, check updates, and verify the recent videos are visible. If possible, re-check after a new upload and confirm there is no duplicate notification.",
            TestDestination.ANIME_SEARCH),
        TestCase("peertube_source", "PeerTube video search and playback",
            "Search PeerTube for animation, open a video, confirm picture/sound and verify unsupported videos show meaningful errors.",
            TestDestination.ANIME_SEARCH),
        TestCase("soundcloud_source", "SoundCloud audio and background controls",
            "Search SoundCloud for an audio drama, play it, lock the phone, use notification controls, and confirm audio continues and resumes.",
            TestDestination.AUDIO_SEARCH),
        TestCase("first_run_js_setup", "Guided first-run JS setup",
            "On a fresh test install, choose content level and then Install sources. In Explore, confirm the progress card updates counts, finishes, and existing built-in manga sources remain usable. Skipping must not install anything.",
            TestDestination.SOURCES),
        TestCase("source_host_failover", "Known-host duplicate handling and fallback",
            "Search for a title found on a site offered by multiple JS engines. Check there is one website section, and when its preferred engine fails or is empty, the second engine is attempted. Different types from the same host must stay available.",
            TestDestination.SOURCES),
        TestCase("source_targeted_repair", "Targeted recovery after repeated failures",
            "Use an installed JS source that has a broken or old parser. After repeated qualified errors the app should check that source for a newer version without reinstalling every other source; try again after repair and report any error.",
            TestDestination.SOURCES),
        TestCase("source_health", "Persistent source health and manual recovery",
            "Search from multiple sources, then examine Explore. Repeated source failures should move unhealthy sites to the bottom with a warning; offline and CAPTCHA interruptions should not penalize them. Manually retry a source to verify recovery.",
            TestDestination.SOURCES),
        TestCase("default_presets", "First-run media source presets",
            "Open Explore and Presets. New installations should offer BL & Yaoi, Popular manga, Books & fanfic, Anime & video, and Audio. Check that editing or deleting a preset does not overwrite your choices.",
            TestDestination.SOURCES),
        TestCase("extension_recovery", "Extension updates and rollback",
            "Open Sources, check the installed-extension update status, and use Check installed source updates. Confirm an updated source still searches and opens content. Report broken sites; staged rollback needs device verification.",
            TestDestination.SOURCES),
        TestCase("extensions", "JavaScript extension management",
            "Install, configure, update and remove a test source. Are settings saved and searches working?",
            TestDestination.SOURCES),
        TestCase("dart", "Dart compatibility alternatives",
            "Check Dart alternatives. An equivalent JavaScript/Kotatsu source may work; direct Dart execution is not yet supported.",
            TestDestination.SOURCES),
        TestCase("downloads", "Downloads and offline use",
            "Download content, turn off networking and reopen it. Confirm saved files and errors.",
            TestDestination.DOWNLOADS),
        TestCase("backup_sync", "Backup and cross-device sync",
            "Back up and restore a test library. If sync is configured, check another device. Flag unimplemented options.",
            TestDestination.SETTINGS),
        TestCase("customization", "Dark mode, phone and tablet layouts",
            "Change light/dark mode, font size and navigation. If possible, compare phone and tablet.",
            TestDestination.SETTINGS),
        TestCase("page_translation", "Translate a manga page",
            "Open a Japanese, Korean or Chinese raw chapter, tap the circled options button, then Translate page. Is the English readable and placed over the speech bubbles?",
            TestDestination.HOME, highlight = listOf(R.id.button_options)),
        TestCase("title_translation", "Translate title and description",
            "Open a title with a Japanese/Korean name or description, tap the circled ⋮ menu, then Translate title and description. Tap it again to switch back.",
            TestDestination.HOME, highlightOverflow = true),
        TestCase("private_categories", "Private categories",
            "Open Library (circled), long-press a category tab and choose Make private. Leave the app and come back: it should be hidden (also in search and the widget) until you unlock it from the ⋮ menu.",
            TestDestination.LIBRARY, highlight = listOf(R.id.tabs, R.id.nav_favorites)),
        TestCase("video_extras", "Video speed, skip intro and picture-in-picture",
            "Play an episode and open the circled ⋮ menu: try Skip intro, Playback speed and Picture-in-picture. Press Home while playing; the video should keep going in a small window.",
            TestDestination.ANIME_SEARCH, highlightOverflow = true),
        TestCase("discover", "Discover lists (AniList)",
            "Try each circled list, including BL 18+. Tap a cover: it should search your sources for that title. Long-press tries the Japanese title.",
            TestDestination.DISCOVER, highlight = listOf(R.id.chips_feed)),
        TestCase("discover_media_hub", "Discover all media kinds",
            "Use the highlighted Manga, Books, Video, Audio and News chips. Are the labels clear? Do books and videos open their own sources instead of manga?",
            TestDestination.DISCOVER, highlight = listOf(R.id.chips_media)),
        TestCase("discover_public_books", "Browse public ebooks in Discover",
            "Open Discover > Books, then choose Public ebooks. Are covers, titles and official book pages useful? Switch back to light novels and confirm it searches novel sources.",
            TestDestination.DISCOVER, highlight = listOf(R.id.chips_feed)),
        TestCase("discover_public_videos", "Browse public videos in Discover",
            "Open Discover > Anime & Video, choose Archive videos and tap a film. Does it open the official video page? Switch back to anime recommendations and confirm global search still works.",
            TestDestination.DISCOVER, highlight = listOf(R.id.chips_feed)),
        TestCase("discover_public_audio", "Browse public audiobooks in Discover",
            "Open Discover > Audio. Are actual audiobook covers visible? Open a title and try playing the provider's audio, then search installed audio sources.",
            TestDestination.DISCOVER, highlight = listOf(R.id.chips_feed)),
        TestCase("entertainment_feeds", "Fiction podcasts and video RSS feeds",
            "Open Podcasts & video RSS from More or Discover. Add a real HTTPS fiction/audio-drama or entertainment video feed, check covers, test playback, saved subscriptions, unsubscribe, and filters. Does the UI explain errors?",
            TestDestination.MEDIA_FEEDS),
        TestCase("standard_ebooks_public", "Standard Ebooks fiction releases",
            "In Books Catalog tap Standard Ebooks (new). Check fiction listings without entering a search and open the official page. This is the public new-releases feed, not the members-only OPDS catalog.",
            TestDestination.BOOKS, highlight = listOf(R.id.monogatari_catalog_standard_ebooks)),
        TestCase("discover_feeds", "Discovery lists and recommendations",
            "Switch popular, BL, light novel and anime feeds. Check that cards search the correct media type and that you like the selection.",
            TestDestination.DISCOVER, highlight = listOf(R.id.chips_feed)),
        TestCase("books_catalog_ui", "Books catalog layout",
            "Try Gutenberg, Open Library, Archive and any other catalogs. Is the book search, import and OPDS navigation intuitive on your device?",
            TestDestination.BOOKS, highlight = listOf(R.id.monogatari_catalog_query)),
        TestCase("book_catalog_all", "Search across every public book catalog",
            "Tap Search all catalogs. Try a title that exists on multiple providers. Are duplicates merged without losing any source? When one catalog fails, do other titles still appear?",
            TestDestination.BOOKS, highlight = listOf(R.id.monogatari_catalog_all)),
        TestCase("book_catalog_covers", "Book covers and publication details",
            "Search Open Library, Gutenberg, Archive, Wikisource or Google Books. Check cover art, author, date, language, EPUB/preview status, and whether every source opens the correct legal page.",
            TestDestination.BOOKS, highlight = listOf(R.id.monogatari_catalog_results)),
        TestCase("book_catalog_sort", "Book oldest/newest and unknown dates",
            "Tap Sort: Best match and choose Oldest first, then Newest first. Are dates sensible and undated titles last? Does this work when providers disagree?",
            TestDestination.BOOKS, highlight = listOf(R.id.monogatari_catalog_sort)),
        TestCase("unified_library_ui", "Unified library navigation",
            "Check All, Manga, Books, Video and Audio. Is everything accessible even when empty? Search your library and open an item.",
            TestDestination.UNIFIED_LIBRARY, highlight = listOf(R.id.monogatari_library_tabs)),
        TestCase("news_feed_ui", "News and followed tags",
            "Open the news feed, add a topic and try followed-only and saved articles. Can you comfortably read and find older articles?",
            TestDestination.NEWS, highlight = listOf(R.id.monogatari_news_filter)),
        TestCase("year_order", "Oldest/newest publication ordering",
            "Open a global search, choose Publication years and try Oldest first, Newest first and Best match. When years are unknown, does the estimate behave sensibly?",
            TestDestination.MANGA_SEARCH, highlightOverflow = true),
        TestCase("tablet_adaptive", "Tablet and split-screen layout",
            "Resize or rotate on a large-screen device. Are the library, Discover, Books and News readable without clipping or oversized cards?",
            TestDestination.UNIFIED_LIBRARY, highlight = listOf(R.id.monogatari_library_tabs)),
        TestCase("visual_feedback", "Theme colors and multicolor styling",
            "Try appearance colors and browse different media sections. Do the colors help distinguish sections without harming text contrast?",
            TestDestination.SETTINGS),
        TestCase("source_scan_timeout", "Source scan screen stays awake",
            "Start a long source test while this screen is visible. The display should stay awake until the scan stops or you leave. It should return to normal afterward.",
            TestDestination.SOURCES),
        TestCase("foreign_import", "Import from Kotatsu or Mangayomi",
            "Choose a Kotatsu or Mangayomi backup, read the summary, then Restore/Import. Check that your titles, categories and reading progress arrived.",
            TestDestination.IMPORT),
        TestCase("translate_toggle", "Translated / original page toggle (#24)",
            "Translate a raw page (circled options button, then Translate page). On the result, tap the Show original button at the bottom, then Show translation. Zoom should stay where it was.",
            TestDestination.HOME, highlight = listOf(R.id.button_options)),
        TestCase("translate_languages", "Per-series translation languages (#25)",
            "In the reader options choose Translation languages for this series. Pick the series language and a target language other than your device language, then translate a page. The language should be remembered for this series only.",
            TestDestination.HOME, highlight = listOf(R.id.button_options)),
        TestCase("translate_correct", "OCR confidence and tap-to-correct (#26)",
            "On a translated page, tap a speech bubble. You should see the recognised text and how sure the recognition was. Type a better translation and save: the page should redraw with your text, and it should stay corrected when you reopen the page.",
            TestDestination.HOME, highlight = listOf(R.id.button_options)),
        TestCase("translate_bubbles", "Bubble grouping and text fitting (#21, #23)",
            "Translate a page with multi-column vertical Japanese bubbles. Columns of one bubble should become one sentence, and long English should shrink or grow to stay inside the bubble instead of being cut off.",
            TestDestination.HOME, highlight = listOf(R.id.button_options)),
        TestCase("explore_vs_discover", "Explore vs Discover (#8)",
            "Open Discover: the intro explains it recommends titles, and every section ends with a Browse source websites button that lands on Explore. In Explore, the ⋮ menu has Discover recommendations.",
            TestDestination.DISCOVER, highlight = listOf(R.id.discover_intro)),
        TestCase("library_continue", "Library Continue strip (#15)",
            "Open the unified library after reading or watching something. A Continue row of your recent titles appears above the list and follows the selected tab (All, Manga, Book, Video, Audio). Tap one to resume.",
            TestDestination.UNIFIED_LIBRARY, highlight = listOf(R.id.monogatari_library_tabs)),
        TestCase("desktop_drawer", "Desktop-width navigation drawer (#62)",
            "On a window at least 1000dp wide (large tablet landscape, Chromebook, desktop mode) the navigation starts as an expanded labelled side drawer; the menu button collapses it. Phones keep the bottom bar.",
            TestDestination.HOME),
        TestCase("stability", "Performance and other bugs",
            "Use the app normally, rotate, switch apps and note crashes, loops, slowdowns and missing features.",
            TestDestination.HOME),
    )

    fun report(
        version: String, device: String, preflight: String, scanSummary: String,
        findings: Map<String, TestFinding>,
    ): String = buildString {
        appendLine("MONOGATARI GUIDED TEST REPORT")
        appendLine("App: $version")
        appendLine("Device: $device")
        appendLine("Automatic probes and user feedback; NOT a guarantee of complete compatibility.")
        appendLine()
        appendLine("AUTOMATIC PRE-FLIGHT")
        appendLine(preflight.ifBlank { "Not run" }.take(1600))
        appendLine()
        appendLine("SOURCE DIAGNOSTICS")
        appendLine(scanSummary.ifBlank { "Not run" }.take(6500))
        appendLine()
        val passes = cases.count { findings[it.id]?.result == CheckResult.PASS }
        val failures = cases.count { findings[it.id]?.result == CheckResult.FAIL }
        appendLine("USER CHECKLIST: $passes looks good, $failures needs fixing, ${cases.size - passes - failures} not tested")
        appendLine("FEATURE FEEDBACK: " + ExperienceRating.entries
            .filter { it != ExperienceRating.UNRATED }
            .joinToString(" · ") { rating ->
                "${rating.label}=${cases.count { findings[it.id]?.feeling == rating }}"
            })
        for (test in cases) {
            val finding = findings[test.id] ?: TestFinding(CheckResult.UNTESTED, "")
            appendLine("[${finding.result.name}] ${test.title} (${test.id}) — feeling: ${finding.feeling.label}")
            if (finding.notes.isNotBlank()) {
                appendLine("  Notes: " + finding.notes.trim().take(900).replace("\r", "").replace("\n", " | "))
            }
        }
        appendLine()
        appendLine("Add reproduction steps/screenshots as needed. Nothing was automatically uploaded.")
    }
}
