# Monogatari work checkpoint

## 2026-10-15 — Release follow-through

- GitHub Actions for `5b5b988` passed both full unit/build/R8 and cold-start emulator jobs (`38667817650`).
- Independently queried all six built-in entertainment/news RSS endpoints: six HTTP 200 XML responses. Two included podcasts are 1.2–1.7 MB, exceeding the existing 1 MB feed cap. Increased bounded podcast feed budget to 4 MB; HTTPS-to-HTTP redirects are now rejected before following them.
- Fixed an additional duplicated illustration in filtered Suggestions and outdated QA wording.
- Aligned the 83-feature tracker with source reality: 56 DONE, 20 PARTIAL, 7 PENDING, 5 DROPPED Windows-only (per owner's emulator decision). Third-party API providers are marked PARTIAL until credential/device testing, not falsely completed.
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
- The 88-feature roadmap still contains partial/pending work. Do not claim all done; the 5 Windows-client milestones were dropped by the owner in favor of an emulator.


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
