# Monogatari steps 52–56 batch checkpoint — 2026-10-13

## Step 52 — compact grid/details (not finished)
- Inspected `ListMode.kt`, `MangaListMapper`, `MangaListFragment`, `ListConfigBottomSheet`, `item_manga_grid.xml`. The existing grid already has cover-overlay scrim/title, unread badge, favourite mark, and reading ring; converting into a selectable Compact mode requires mapping and picker updates. No misleading new mode is exposed yet. Details header work pending.

## Step 53 — new-install dark AMOLED (partially implemented)
- `AppSettings.kt`: when theme preferences are absent, default night mode to `MODE_NIGHT_YES` and AMOLED dark mode to true. Existing explicitly persisted theme/night and AMOLED preferences take precedence. Palette selection is still existing ColorScheme.default; no separate Monogatari palette installed.

## Step 54 — Kototoro translation engine (upstream research only)
- Queried original `Kototoro-app/Kototoro` source trees `reader/translate/domain/` (29 files) and `reader/translate/data/` (11 files) through Tunnel. Not ported: `MlKitReaderOcrEngine.kt`, `OnnxBubbleDetectorEngine.kt`, `ReaderBubbleRoiOcrCoordinator.kt`, `ReaderTranslationRenderStyle.kt`, `OnnxReaderTranslationEngine.kt`, `ReaderTranslationTextCache.kt`. Need license-preserving port, ONNX model management, Android page renderer and real device QA. Do not represent a translation engine as operational.

## Step 55 — translation preferences (foundation only)
- New pure `SeriesTranslationPreferences`: explicit enable/disable decision overrides language default; foreign language defaults to on, unknown language to off. Adds deterministic longest-glossary-match substitution and JVM unit tests. Not wired to per-series UI/persistence or offline fallback/background workers.

## Step 56 — title translation policy (foundation only)
- New pure `TranslationTextPolicy`: prefer an English/Latin alternative title without replacing stored original, and use language-and-text SHA-1 content cache keys; JVM unit tests. No ML Kit language/translation runtime, Room text cache, display toggle or translated-query fanout.

## Old regression
- CI `38400572933` failed compile because nullable `Manga.coverUrl` used `.isBlank()` in `RecentListFactory` and `ShelfListFactory`. Fixed with `.isNullOrBlank()` in this batch.

## Release safety
- All five Steps 52–56 remain incomplete, with Step 52/54 reviewed and Steps 53/55/56 coded partially. New source must pass exact-commit CI before release. Preserve worktrees and existing users' data; no automatic cloud translation or credential transmission.
