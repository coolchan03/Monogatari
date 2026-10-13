# Monogatari v0.13 eight-step batch ledger — 2026-10-13

## Release verification (separate from next unfinished stages)
- Exact commit `d01583fd109e584e27242d1957790eae8cd8bd97` passed GitHub Build `38392011683` (JVM, debug APK, unsigned R8).
- Published PRE-RELEASE `v0.13.0-stagegh-preview`: https://github.com/coolchan03/Monogatari/releases/tag/v0.13.0-stagegh-preview
- Debug APK digest: `0807989c41de05d10be3f83a0be928c2b32454ec42deaa8686c6432c00b42ec7`; debug-signed only; no device verification; `devel` unchanged.

## Exact stage acceptance (Step 44–51 focus; 0 of these eight fully accepted)
- 44 WebDAV: PARTIAL ONLY. Untracked HTTPS endpoint+transport and disabled account-store skeleton; security test added locally. No encrypted secret store, UI, WorkManager or synchronization integration; no end-to-end test.
- 45 Foreign backup imports: PARTIAL ONLY. Untracked format preflight and source matcher with authored tests; no protobuf/JSON schema parser, user review, transactional import, undo or UI.
- 46 Statistics: PARTIAL. Commit `e1860fdfb700e7bc441039c564999bb7e4379eec` caps each reading-event gap to 120 seconds and adds three JVM tests. Build `38394023068` is pending at ledger creation. History cascade foreign key, chapter columns, migration and media stats are not fixed.
- 47 Time estimates: PARTIAL. Pure local untracked TimeEstimator.kt and TimeEstimatorTest.kt use title/media/global ms-per-page fallback, words/minute and media duration. Not wired to ReadingTimeUseCase or chapter metadata.
- 48 Stats screen: NOT STARTED in this batch; no CHAPTER_META or UI.
- 49 Navigation: PARTIAL local AppSettings.kt new-install default order Favorites, Updated, History, Explore; current users retain persisted order. More destination UI not implemented.
- 50 Media library: NOT STARTED; no per-type UI or BookShelf merge.
- 51 Generated covers: NOT STARTED; no GeneratedCoverDrawable or widget integration.

## Data and CI safety
- Never reset worktree: preserve all untracked WebDAV, foreign-migration source/test/checkpoint files.
- `C:\\Users\\coolc\\Codex Projects\\Manga-Reader\\.release-temp\\` and `source-audit` belong to user and were not touched.
- No Android SDK/JDK installed. No direct device testing, no production signing. Do not merge unpublished partial 44–51 code into `devel`.
- Last published/debug-preview commit `d01583fd1`; newer statistics-only branch head `e1860fdfb` requires its own green exact-commit CI.

## Additional Step 51 local implementation
- Created `core/ui/image/GeneratedCoverDrawable.kt`: deterministic offline title-only covers with bounded three-line text and stable color palette; no network dependence.
- `image/ui/CoverImageView.kt`: null/blank or failed manga cover requests now use generated drawable fallback/error.
- `widget/shelf/ShelfListFactory.kt` and `widget/recent/RecentListFactory.kt`: failed cover fetches now display generated cover bitmaps rather than generic placeholder.
- Step 51 remains PARTIAL: generated covers have no device screenshot review and tracker notifications/app shortcuts are not converted; local source is not yet compiled/CI tested.
- Step 50 still NOT STARTED; no fabricated library filtering progress. Steps 48 and 50 require data/UI work and remain pending.
