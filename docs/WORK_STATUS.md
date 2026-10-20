# Monogatari work checkpoint

## 2026-10-20 — v1.2.0 released (public)
- https://github.com/coolchan03/Monogatari/releases/tag/v1.2.0 built from `devel` `c50d9bb` (drafts merged; build check run 39470441626 green; release started by one owner-approved connector workflow dispatch; not draft, not prerelease). APK SHA-256 `e3adc2dd527fe5397d90f337f159551b9c3786eab6a57e0f3a3707e5e5f8cac1`.
- Not device-verified yet.

## 2026-10-20 — v1.2 candidate batch (drafts branch `93c1e29`, CI run 39433462300 green, unreleased)
- Main search bar: filter icon picks All/Manga/Books/Video/Audio (remembered, prefs `monogatari_search`).
- NewPipe: no-query lists use kiosks (YouTube trending_movies_and_shows/music/gaming; PeerTube Trending; SoundCloud New & hot), fallback to the old fixed search.
- New Discover books / Discover videos pages (discover/media/): parallel cover shelves, scoped search, chips; optional nav items + More entries.
- Airing reminders: follow from "Airing this week" (long-press); worker every 3 h; prefs `monogatari_airing_reminders`.
- Nearby: history conflict review sheet (SyncConflictPlanner); new per-device ledger prefs `monogatari_nearby_sync_ledger`.
- Earlier in this batch: Discover as a tab, search type chips, Discover shelves, Books catalog autoload, feed fixes + cache, restyled secondary screens, restore settings groups, Airing this week.
- Status: implemented, unit tests + build + cold-start smoke green; NOT device-verified (feeds and NewPipe kiosks unreachable from the sandbox).

## 2026-10-20 — 1.0.2 work on devel; v1.1+ progress on drafts branch
- `devel` `c4da23b` (green, run 39346646019): GitHub Actions on Node 24 versions; 124 screen texts moved to `strings_monogatari_ui.xml`; quoted strings keep edge spaces. Not released; version still 1.0.1 until owner device feedback.
- Drafts branch `9372d4c` (green, run 39346922739): Mihon import carries reading position; Nearby receive picks settings groups and offers Send back (two-way sync); smart collections in the Library; Discover "For you" ranking from local history; Test Center checks for these.

## 2026-10-20 — post-1.1.0 batch (drafts branch, unreleased)
- Search: Manga/Books/Video/Audio chips on the results screen (phone + tablet layouts); global type filter now also hides history/favourites/local rows.
- Discover: Video/Books/Audio open on a shelf of popular titles from built-in sources (Dailymotion, Odysee, IA Video; Gutenberg, Royal Road, Open Library; LibriVox, IA Audio); Books catalog opens with the last search or Standard Ebooks new releases.
- v1.3 draft: "Airing this week" chip in Discover Video (AniList airingSchedules, 7 days, episode + local time badge).
- v1.1 follow-up: normal backup restore asks which settings groups to apply (shared SettingsGroupsDialog, also used by Nearby).
- Feeds: OkHttp fetch with browser UA, https-only redirects, lenient FeedXml parsing (Android rejected the old secure-processing flag), any public https feed, included_feeds_v2 (9 feeds), per-feed article cache, All feeds chip, mascot empty state.
- UI: core/ui/MonogatariViews.kt; backup import, Nearby, WebDAV, licences, StoryWeaver, feed player restyled with header/section cards.
- Status: implemented; CI in progress; feed hosts unreachable from the sandbox; not device-verified.

## 2026-10-20 — Discover as a normal tab (after v1.1.0, unreleased)
- Owner report: Discover opened as a separate page over the main screen (back arrow, navigation bar hidden).
- New `discover/DiscoverFragment.kt` (logic moved from the activity) is the `nav_discover` tab in `MainNavigationDelegate`; Explore's "Discover" menu switches to that tab when it is on the bar. `DiscoverActivity` is now only a host for the fragment, used from More / Test Center or when Discover is not on the bar.
- Status: implemented; CI pending; not device-verified.

## 2026-10-20 — v1.1.0 released
- https://github.com/coolchan03/Monogatari/releases/tag/v1.1.0 built from `devel` `3d3e25a` (drafts branch merged; build check run 39349438710 green; tag push blocked by proxy, release started by one owner-approved connector workflow dispatch). APK SHA-256 `f1b102fdefd3af9322ccd5febaedd7617a108264d7d1a82e20c92cc109308662`.
- Implemented and CI/unit-tested, NOT device-verified: Mihon import, Nearby two-way sync with settings groups, Discover "For you", smart collections, side-by-side translation, player auto-next, EPUB read-aloud.
- Audit note (2026-10-20): style grep found fixed 15sp text in OpenSourceLicensesActivity and ForeignBackupReviewActivity; no screen has been visually checked (no screenshots yet). Next proposed: CI screenshot pass (light/dark), tablet/Razr layout check, real .tachibk import test.

## 2026-10-19 — v1.0.1 released
- https://github.com/coolchan03/Monogatari/releases/tag/v1.0.1 built from `devel` `1d06598` (build check run 39330141224 green; release started by one owner-approved connector workflow dispatch). APK SHA-256 `831005d4ce7744689f8629011f6bb6c04019d9ef913d178d76e34f155b4714ca`.
- Contents: owner feedback fixes, full-resolution Kitsune art, consistent screens (see `docs/RELEASE_1_0_1_NOTES.md`). v1.1+ drafts stay on `claude/monogatari-v1-release-sq1t2k`, which has `devel` merged back in.
- Next: owner device test with the Test Center; 1.0.2 candidates: move hard-coded screen text to string resources, update deprecated GitHub Actions (checkout/upload-artifact/setup-gradle v4).

## 2026-10-19 — v1.0.1 release line
- Built on `devel` (v1.0.0) as local `release-1.0.1`: owner-feedback fixes (cherry-picked `d20cb3b`), new Kitsune art (`182b9b5`), and a screen-consistency pass (Books, Library, feeds, News, Discover, EPUB/PDF readers, player, Nearby, WebDAV, backup import, Source log, StoryWeaver, fiction browser). v1.1+ drafts excluded.
- Operation: push to `devel`, wait for build check, start the release workflow for `v1.0.1`, verify APK and SHA-256, then merge `devel` back into `claude/monogatari-v1-release-sq1t2k`.

## 2026-10-19 — v1.0.0 released; owner feedback batch (v1.0.1 candidate) and post-1.0 drafts
- **Released:** https://github.com/coolchan03/Monogatari/releases/tag/v1.0.0 built from `85343a2` (run 39272192778). APK SHA-256 `809bafa5a812fb02693adb6b14f6dd67c917afcbd332855c35a71c8e14355fcb`. The release was started with one owner-approved GitHub connector workflow dispatch (this session cannot push tags).
- Owner feedback fixed in code: theme now follows the phone until chosen (was forced dark); Settings gear always on the main toolbar; podcast/RSS and News screens rebuilt with chips + menu like the rest of the app, with included feeds pre-installed and News loading immediately; Discover actions are chips; larger YouTube/Dailymotion/Twitch thumbnails.
- Image audit: Kitsune stickers are 64x64 originals (scripts/kitsune/sticker_atlas.b64) enlarged 4x and reduced to 32 colours, which is why they look soft. No higher-resolution originals exist; the deleted vector Kitsune set is still in git history. New art or a decision is needed.
- Post-1.0 drafts on this branch: Mihon import, sync conflict planner, settings transfer groups, taste profile, side-by-side translation, smart collections, Komga client, auto-next episode, EPUB read-aloud. See FUTURE_VERSION_ROADMAP.md.

## 2026-10-19 — v1.0.0 release preparation (owner approved: "do everything")
- Review pass over all unmerged finishing code: fixed WebView handling so provider CAPTCHA frames (e.g. challenges.cloudflare.com) can load inside FanFiction.net/FictionPress/StoryWeaver pages, and routed their outside links through the safe-link check.
- Version 1.0.0 (versionCode 18). Tracker 83 DONE in code; device acceptance follows the release.
- Release workflow also runs on a pushed `v*` tag (git route, no connector write). Notes from `docs/RELEASE_1_0_0_NOTES.md`.
- Next operations: push branch, green build-check, merge into `devel` (merge commit, no history rewrite), green build-check on devel, push tag `v1.0.0`, verify the signed APK, release commit and SHA-256.

## 2026-10-19 — finishing batch 2
- Operation: commit and push batch 2 (#21, #23, #32, #66, #14, #63/#69) to `claude/monogatari-v1-release-sq1t2k`, then read build-check for the new head.
- Not verified on device.

## 2026-10-18 — Claude cloud session, v1.0 finishing batch 1
- Branch `claude/monogatari-v1-release-sq1t2k` from finish-83 head `a3837b2`. Push via this container's git remote (owner approved; no Tunnel to Surface tool here). GitHub connector is read-only. No local SDK (owner declined), so GitHub Actions does the compiling.
- Baseline build-check run 39176557261 on `a3837b2`: unit tests PASS, debug APK PASS, R8 release was still running at the time of writing.
- Operation: commit batch 1 (diagnostics triage, per-source search retry/CAPTCHA action, a11y labels, future roadmap), push, then read the build-check result for the new head.
- Pushed `b303510` (4 commits on top of `a3837b2`). Build check run 39177526228: unit tests, debug APK, unsigned R8 release and Android 15 cold-start emulator smoke test all SUCCESS. Baseline run 39176557261 on `a3837b2`: build job SUCCESS, cold-start cancelled by the push.
- Not verified: no device, provider or TalkBack test. The 14 PARTIAL items stay PARTIAL.

## 2026-10-15 — Release follow-through

- GitHub Actions for `5b5b988` passed both full unit/build/R8 and cold-start emulator jobs (`38667817650`).
- Independently queried all six built-in entertainment/news RSS endpoints: six HTTP 200 XML responses. Two included podcasts are 1.2–1.7 MB, exceeding the existing 1 MB feed cap. Increased bounded podcast feed budget to 4 MB; HTTPS-to-HTTP redirects are now rejected before following them.
- Fixed an additional duplicated illustration in filtered Suggestions and outdated QA wording.
- Aligned the 83-feature tracker with source reality: 56 DONE, 27 PARTIAL, 0 PENDING across 83 active Android milestones (seven sources now have code paths; provider testing pending). Third-party API providers are marked PARTIAL until credential/device testing, not falsely completed.
- Pending after code release: fresh GitHub CI and device feedback for high-resolution art, video subtitles, live server credentials, and remaining optional sources.


## 2026-10-14 — User-recalled enhancements (source patch prepared, pending GitHub Actions)

- Requests: Kotatsu/Mangayomi-inspired selectable appearance; All/Manga/Books/Video/Audio search modes; included entertainment and news RSS; replace duplicate/stale/blurry imagery; simultaneous multi-source tests that keep the screen awake.
- Files touched: AppearanceSettingsFragment + pref_appearance + strings, SearchMenuProvider/SearchViewModel + opt_search_kind, NewsFeedActivity/Policy and MediaFeedActivity/Playlist, JsSourceDiagnostics/SourceScanManager/SourceSweepCheckpoint, PublicBookCatalog, eight empty-state view models, tests and image audit documentation.
- Source probes: Kotlin coroutines limited to 4 simultaneous built-in and 2 installed JS sources; checkpoint appends synchronized. Both diagnostic screens already set FLAG_KEEP_SCREEN_ON only when resumed and actively scanning.
- Images: no exact duplicate binaries among 56 PNG/WebP images; Kitsune sticker originals are 256x256 and need better masters for large usage. Improved Open Library and Google Books remote covers and reassigned repeated empty-state forms; remaining device image inspection is NOT completed.
- On-device feedback: guided test center now asks separately about theme styles, scoped search, feeds/playlists, image quality and concurrent scans.
- Local build unrun (no JDK/SDK installed); push then verify GitHub Actions before marking tested. Never replace or reset the user's original dirty P: checkout.


## 2026-10-14 — Continue partial/pending requirements (active)

- Canonical integration target: GitHub `devel`. Claude's more recent `ccr-94e2bfd8-3w4etd` at `d3119cb` is 45 commits ahead of the stale default, including mascot, large-screen, OCR, backups and media providers. Keep ALL of those changes.
- Protected: original dirty `P:\\AI Projects\\Monogatari`; development worktree `C:\\Users\\coolc\\Monogatari-Audit-Final` from the newest Claude branch. Previous stale-baseline repair commit `f6598a7` is retained but not integrated wholesale.
- Implemented in current working batch: preferred book catalog provider and stable grouped identity; import cleanup after failed/cancelled file copy; feed HTTPS URL normalization and redirect checking; Podcasting 2.0 transcript/subtitle links, duration/chapters preservation, DASH/HLS classification; cancellation of removed feed in-flight loads; regression tests.
- Next exact operation: check diffs and tests, commit and push through Tunnel to Surface, then run GitHub Actions. No Android SDK/JDK installed locally; device-only playback and OEM startup must not be claimed as verified.
- The Android-only 83-feature roadmap tracks incomplete provider and device testing. Native Windows development is not in scope.


## 2026-10-13 — Startup crash diagnosis (current)

- **Canonical GitHub branch:** `devel` (only active remote branch; old tips preserved by archive tags). Do not create another development branch.
- **Previously merged head:** `a029f45eac9f143bc0293d0350a08753aa4574fd`; Claude's changes plus guarded ACRA lifecycle callbacks. CI build passed but did not launch app.
- **Repair head:** `e2c72be15b4a9a81d3282198650918fc85e566b6`; first diagnostic build `5169d2f` reproduced the crash, second build verified correction.
- **Observed issue:** User reports immediate on-device crash and ~4.10 KB of app data. APK packaging inspected: prior debug APK is ~47 MB with arm64-v8a native libraries. 4 KB is saved data, not APK size.
- **Crash reproduced:** Android 15 smoke run `38467173560` failed at `NetworkModule.provideBaseHttpClient(NetworkModule.kt:68)`: `IllegalStateException: Calling this from the main thread is prohibited`, during Hilt application injection. This is a confirmed cause of the pre-screen launch failure.
- **Fix shipped and verified:** `e2c72be15b4a9a81d3282198650918fc85e566b6` removes the invalid main-thread assertion without weakening TLS, guards ACRA-disabled paths, and bumps to v0.13.1. GitHub Actions [38469889807](https://github.com/coolchan03/Monogatari/actions/runs/38469889807) passed unit tests, debug APK build, R8 release build and a real emulator cold-start smoke test. The signed debug APK is [published here](https://github.com/coolchan03/Monogatari/releases/tag/v0.13.1-launch-fix-debug). Motorola device testing is still outstanding.
- **Device debugging:** No authorized device was attached to `adb` when checked; do not claim the phone crash has been reproduced without a log. Do not uninstall or clear data.
- **Next step:** install and test the published v0.13.1 debug APK on the Motorola device. If a device-specific crash remains, use `scripts/capture-startup-crash.ps1` to collect a targeted stack trace; never clear saved data without a backup.

## Historical checkpoint — 2026-10-12

- Date: 2026-10-12, America/Chicago
- Current branch: batch-08-10-extension-security.
- Last completed milestone: Steps 4-5 passed GitHub Actions run 38292011053 and fast-forwarded to default devel at e497d88de. Verified by git ls-remote.
- Current operation: Step 8 partial, replacing hardcoded JS source index list with BuildConfig.DEFAULT_REPOS, configured from monogatari.repos or MONOGATARI_REPOS. Existing 15 URLs set through tunnel as GitHub Actions secret (confirmed by gh secret list).
- Files edited: app/build.gradle, SourceRepoStore.kt, .github/workflows/build-check.yml, .github/workflows/publish-release.yml.
- Remaining Step 8 tasks: Implement and test monogatari://setup?repos= handler, verify workflows and Gradle compile; do not claim completion until these pass.
- Steps 9-10 not started, despite batch branch name.
- Next exact action: git diff/code QA, commit and push Step 8 partial; inspect CI. If failure, diagnose and fix before default-branch merge. Then implement deep link and scope JS extension cookie access.
- Important: Only edit/push GitHub through Tunnel to Surface. On every new turn, call Tunnel to Surface FIRST.
- Safety: Existing untracked .release-temp/ and source-audit/ left untouched. Installed old app backup/restore still requires manual test. Signed releases require keystore; do not publish unsigned APK as signed.
- Timeout behavior: Cannot detect exact ChatGPT timeout moment; use this file and Git/CI timestamps for last durable checkpoint.
`n## 2026-10-14 - Bucket 1 mascot integration IN PROGRESS`n- Through Tunnel to Surface: editing current checkout ccr-94e2bfd8-3w4etd, commit e89d11adc.`n- Scope: 8 simple Kitsune sticker roles, hidden muscular male form, theme-aware accents, UI placement, retire old vector placeholders, available CI build/test.`n- Do not claim bucket 1 complete until committed, pushed and build checked; phone review remains external.

- Current local milestone: 8 compressed app-ready sticker PNGs and matching legacy/adaptive launcher icons, widgets and store art exported; theme-aware reusable renderer; Home, More, Discover, Books, empty states and hidden male form wired. CI build and push still outstanding.

## 2026-10-14 — Bucket 2 large-screen integration (active)
- Scope: true search-and-details dual-pane at >=840dp; improve tablet library, book catalog, portrait/landscape details, keyboard usability and large-screen insets.
- Start head: 1cc79aad8 on ccr-94e2bfd8-3w4etd. Preserve data, apps and source IDs. Do not install SDK locally.
- Next: inspect layouts, add tested width policy, implement and push via Tunnel to Surface; confirm CI.

## 2026-10-15 - Remaining 14 Android items, isolated finishing worktree
- Source: published devel `eaae55c1fcfe9972809e95974396759fd8968f0f`, 69 DONE, 14 PARTIAL. PR list empty at initial check; latest build-check still running.
- Working branch: `finish-83-20261015`; new isolated worktree `C:/Users/coolc/Monogatari-Finish83-20261015` clean at creation. Original dirty P: worktree and older audit worktrees untouched.
- Active operation: implement and test outstanding OCR bubble geometry and translated text fitting (#21/#23), then additional feasible roadmap gaps; verify GitHub-hosted CI before any completion claims.
- No SDK/JDK/emulator installation on owner devices. All pushes, PRs, merges, dispatches and release changes through Tunnel to Surface. Do not release v1.0.0 until all items validated.

## 2026-10-15 - OCR, catalog, responsive and cross-source finishing branch
- Pushed `a4605f1` (#21/#23), `612f4f3` (#14/#17/#63/#66) and `6370587` (#37) to `finish-83-20261015` through Tunnel. None has yet been merged into `devel`.
- CI `38841599631` on `6370587` failed compiling the new `MangaDuplicatePolicyTest.kt` because its final class brace was omitted. Fixed in working tree. This is not a production regression in application code; fresh CI still required.
- Verified StoryWeaver public `https://storyweaver.org.in/api/v1/books-search?query=cat&per_page=2&page=1` returns HTTP 200 with 1931 real matching public story records (title/author/illustrator/cover/publisher). Implemented parser, official-source reader WebView and official-site fallback, and tests. No invented PDF/EPUB link, site scraping or anti-bot bypass.
- Still open: cross-media filters (#32), global chrome/dark/accessibility (#11/#18/#69), foldable/window/device acceptance (#66), provider restrictions (#48), private Plex credentials/device codec (#58). Await hosted unit/R8/cold-start and real owner-device QA; not cleared for 1.0.0.

## 2026-10-15 - Metadata-aware filters finishing work (#32)
- Implemented explicit Search > filter menu for available result tags/demographics, normalized source language, public catalog formats, and actual local history (opened/not opened), preserving choices via SavedStateHandle.
- Unsupported reading-direction metadata is deliberately not fabricated; controls appear only when at least one result exposes relevant metadata. Public external catalogs are excluded from local-history selection and genre selection when no genre metadata is supplied.
- Added `SearchMetadataFiltersTest`; still awaiting exact-commit GitHub-hosted unit tests, R8 APK and cold start. Do not count #32 fully DONE until those checks and unsupported direction behavior are reviewed.
