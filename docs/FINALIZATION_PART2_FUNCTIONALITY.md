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
