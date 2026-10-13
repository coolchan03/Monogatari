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

## Additional Step 48 and Step 50 work
- Step 48 PARTIAL: `StatsRollup.kt` groups reading-session durations into user-timezone calendar days, with `StatsRollupTest.kt` covering same-day aggregation and timezone boundaries. `StatsDao.findSince` and an internal repository method retrieve recent data. No chart/heatmap/streak UI, chapter metadata or backup section yet.
- Step 50 PARTIAL: converted the local Books shelf Fragment from fully inflated ScrollView/LinearLayout cards to a RecyclerView adapter; book rows reuse `BookShelfCard.bind` shared with the catalog, preserve filtering and reader click behavior. The category-level Manga/Books/Video/Audio switch and integration of local shelf books as normal favourites remain pending.
- Therefore all eight Steps 44–51 have now been **touched**, but **ZERO of the eight are accepted fully**. Stage 44/45 remain disabled; Step 46 only fixes idle gaps; Step 47/48 not wired to complete screens; Step 49 default navigation only; Step 50 shelf-only RecyclerView; Step 51 generated covers only on normal manga requests and widgets.
- The newer work is local, not part of v0.13.0 preview or verified by Android CI. Do not merge/release it without exact-commit passing checks.

## Follow-up large batch (2026-10-13 morning)
- Previously isolated commits `7c7a06006`, `f569f2467`, `392e8ca8b` pushed together as cumulative HEAD `392e8ca8b61b741456d52e5a9961aef3db19e7c3` through Tunnel. Exact-commit Build run `38397469870` initiated, not yet accepted at this checkpoint.
- Previous Step 46 idle-time fix `e1860fdfb700e7bc441039c564999bb7e4379eec` passed exact-head Build `38394023068` including JVM, debug APK, R8.
- Step 47 wired `TimeEstimator.mangaRemainingMs` into `ReadingTimeUseCase` using measured ms/page if available, otherwise eight-second default; 20 pages/chapter is still approximate until chapter_meta exists. Added after push: LOCAL ONLY, must be compiled and tested.
- Never include disabled WebDAV as functional or claim the foreign import feature works. Do not auto-migrate data.

## Step 46 database correctness follow-up
- New Room DATABASE_VERSION 31 and Migration30To31 replaces the stats history FK with a manga FK while copying existing statistics, adds nullable `chapter_id`, `words`, `media_ms`, and creates `chapter_meta` with a composite chapter/manga identity, indexed manga foreign key, and size/duration fields.
- StatsCollector now records chapter ID and preserves extra columns during session upserts.
- Data preservation across Android upgrades and Room schema verification are **not yet device-verified**; migration code requires exact-commit CI and upgrade tests before any new release. There is no destructive migration or bulk live user-data operation here.

## History deletion statistics regression (2026-10-13)
- StatsDao.getDurationStats no longer filters on history.deleted_at, which would hide surviving stats after the History table is cleared. It now joins explicitly to manga, consistent with new stats foreign key.
- This is a distinct local follow-up on top of the pushed version-31 migration `6ff6bce24` and needs CI. No device upgrade/migration acceptance claim.

## Five-step focused batch (2026-10-13)
- Step 47: ReadingTimeUseCase clamps estimated pages against Int overflow and coerces persisted history completion percent to [0,1]. This remains a chapter-size approximation, not full reader top-bar integration.
- Step 48: StatsActivity guards pie-chart percentages when total duration is zero; does not complete the requested charts, goals, or backup integration.
- Step 49: New-install default navigation includes the Books destination alongside Library, Updates, History and Browse; existing persisted navigation choices remain untouched. This is NOT the requested More hub.
- Step 50: BookShelfRecyclerAdapter uses stable identifiers derived from local file paths, reducing unnecessary view identity churn. Media-type switch and merged Books favourites remain unfinished.
- Step 51: Shelf and Recent home-screen widgets avoid network requests for blank cover URLs and render generated title covers directly. CoverImageView Coil fallback and notifications/shortcuts integration remain pending.
- All five steps are PARTIAL. Preserve truthful status until exact-commit CI and device acceptance.
