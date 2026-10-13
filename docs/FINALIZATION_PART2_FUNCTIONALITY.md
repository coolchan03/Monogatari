# Monogatari Finalization Part 2/5 — functionality completion pass

Date: 2026-10-13. This is the first integrated UI batch, not full acceptance of all outstanding features.

## Working UI connections implemented
- Statistics screen has explicit `Export reading statistics (CSV)` action opening Android's system CreateDocument picker. The selected period/category data displayed by the existing stats screen is exported using CsvExportFormatter (quotes cells and neutralizes spreadsheet formulas). No file is written without the user's picker selection.
- Statistics screen has `Daily reading totals` action. It requests up to 30 days of already-stored sessions via StatsRepository.getDailyReadingTime and shows a calendar-day summary. No network, background tracking or new permissions.

## Requirements still outstanding
- No production or preview release until exact-commit unit tests, debug, R8 and necessary device acceptance pass.
- Calendar heatmap/streaks, full library CSV, Goodreads/StoryGraph mapping, and statistics backup remain pending.
- Full-screen More navigation, integrated source migration, credential-secure WebDAV, media library type tabs, translation runtime, and private categories remain incomplete. These must not be represented as finished.
- Data migration instrumentation and backup/restore/device checks remain separate from Part 2 and are still unresolved.

## Part 2 continuation, second integrated feature batch
- Local book catalog screen now has an explicit `Export my books (CSV)` menu action. Android CreateDocument picker controls destination; exports title, format and the saved local reading-progress summary through the shared CSV formatter. No exported data leaves the phone automatically.
- Local text/HTML reader now has a 15-minute speech sleep timer and a cancel action. It checks a monotonic clock before speaking each paragraph. This is not a background service and does not claim exact millisecond stopping in the middle of a long paragraph.
- The combined CI result and Android reader smoke tests remain outstanding; do not claim complete Part 2 acceptance. WebDAV sync, encrypted private categories, full media navigation, offline translation, cross-media discovery and remaining integrations are not finished.

## Third UI integration batch
- Main app overflow menu now exposes a **More destinations** dialog linking existing Books catalog, reading statistics, source test center, and Settings activities. Existing bottom-navigation selections and persisted order are unchanged. This is a functional shortcut hub, not the full redesigned More tab.
- This and earlier CSV/TTS changes still require exact-commit CI, launch/navigation smoke testing and user acceptance.
- Part 2 cannot truthfully be marked complete while WebDAV secret storage/synchronization, full foreign importer, privacy enforcement, translation runtime, unified media library and discovery/tag/RSS wiring are missing.

## Fourth implementation batch — actual user-facing integrations
- Added `UnifiedLibraryActivity`: displays favorited manga, book/novel, video/anime and audio by installed source-media type, plus imported local ebooks in the Books tab. Search filters within each type and opens existing details or local reader. Accessed from More destinations; existing library/categories are preserved. Only currently registered sources can be reliably classified, and rows still require visual QA.
- Added `NewsFeedActivity`: user-entered allowlisted HTTPS RSS/Atom feed, optional saved URL list, opt-in manual refresh, persisted followed tags and followed-only filter; article taps open an external browser. Network fetches refuse redirects, cap feed bytes at 1MB and use bounded timeouts. No background fetching, automatic notifications or credential uploads.
- Added secure bounded `NewsFeedParser` for RSS 2.0 and Atom with entity/DTD refusal, HTTPS-only article links, 100-item limit and JVM regression tests.
- Both new activities are non-exported Android manifest entries and visible in the main More destinations menu.
- No claim that encrypted WebDAV, automated tag notifications, private categories, foreign parsers or on-device translation is operational. This improves Part 2, not full acceptance.

## Part 2 manual WebDAV implementation
- `WebDavAccountStore` now implements AES-256-GCM Android Keystore credential encryption/decryption and explicit removal; passwords are never stored as plaintext preferences.
- `monogatari_webdav_secret.xml` excluded from both cloud backup and Android device transfer rules (legacy backup-content rules too).
- `WebDavManualSyncActivity` exposes secure account setup, explicit manual upload of a selected ZIP with bounded 128MB copy and basic ZIP validation, remote device-snapshot listing, selected download via SAF, and account removal. All downloads are user-directed, never automatically restored. Uses the existing HTTPS-only, redirect-disabled transport.
- This is **manual file transfer only**. Automatic background sync, per-record conflict reconciliation, credential recovery after Android backup, hostile WebDAV server tests and multi-device acceptance remain outstanding. Do not market it as complete two-way synchronization.
