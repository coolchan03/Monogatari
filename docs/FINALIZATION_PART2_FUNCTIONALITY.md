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
