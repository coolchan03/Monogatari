package org.koitharu.kotatsu.core.qa

/** Success for a parser network request is not proof that a chapter or video can actually play. */
enum class CheckResult(val label: String) {
    UNTESTED("Not tested"), PASS("Looks good"), FAIL("Needs fixing"),
}

enum class TestDestination {
    HOME, MANGA_SEARCH, BOOKS, BOOK_SEARCH, ANIME_SEARCH, AUDIO_SEARCH, SOURCES, SETTINGS, DOWNLOADS, NONE,
}

data class TestCase(
    val id: String,
    val title: String,
    val instructions: String,
    val destination: TestDestination,
)

data class TestFinding(val result: CheckResult, val notes: String)

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
        for (test in cases) {
            val finding = findings[test.id] ?: TestFinding(CheckResult.UNTESTED, "")
            appendLine("[${finding.result.name}] ${test.title} (${test.id})")
            if (finding.notes.isNotBlank()) {
                appendLine("  Notes: " + finding.notes.trim().take(900).replace("\r", "").replace("\n", " | "))
            }
        }
        appendLine()
        appendLine("Add reproduction steps/screenshots as needed. Nothing was automatically uploaded.")
    }
}
