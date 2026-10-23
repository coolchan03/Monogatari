# Monogatari — new-chat handoff (updated October 15, 2026)

This is the canonical handoff for the **Android entertainment application**. Read this and `docs/ROADMAP_83_TRACKER.md` before editing. Update the handoff after each verified finishing batch.

## Owner's definition of “finished”

Finish all Android in-scope features in the 83-item roadmap **in code**, publish the first official production-signed 1.0.0 APK, and then let the owner test on **two real Android devices** and send a consolidated fix report. Do not mark incomplete work DONE merely because a screen or official-site link exists. Separate **implemented**, **CI-validated**, **live-provider-validated**, and **device-validated**. Existing beta builds are not the first official release.

The Windows desktop/PWA project was abandoned deliberately. Do not restore its five roadmap items, recommend or install Windows emulators, or install Android SDK/JDK/emulator software on the owner's devices. GitHub-hosted Android emulator smoke tests are permitted.

## Repository, environment and publishing rules

- GitHub repository: https://github.com/coolchan03/Monogatari
- Canonical published branch: `devel`. Android-only tracker: `docs/ROADMAP_83_TRACKER.md`; full roadmap: `docs/FINISHING_ROADMAP_20261013.md`.
- Tunnel to Surface connector is used for laptop/GitHub access. **Ask for tunnel access at the beginning of every new chat. All GitHub writes (push, merge, releases, CI dispatches) MUST go through the tunnel.** The GitHub connector may be used for reading only.
- Verified active Windows checkout used in this chat: `%USERPROFILE%\Monogatari-Audit-Final`. It is a Git *worktree*, with the underlying Git directory on `P:\AI Projects\Monogatari`. There are other worktrees and possibly other open chats: **inspect branch, tracked/untracked modifications and remote state before making any change; never reset, discard or overwrite somebody else's work.**
- Git may report “dubious ownership” for the exFAT P: drive. Use per-command `git -c safe.directory="%USERPROFILE%/Monogatari-Audit-Final" -C "%USERPROFILE%\Monogatari-Audit-Final" ...` instead of modifying global security settings.
- Build/test workflows: `.github/workflows/build-check.yml` and `.github/workflows/publish-release.yml`. Run GitHub-hosted unit tests, APK/R8 build, signed-release dry run, Android cold-start test; keep signing keys only in GitHub Actions secrets. Do not mistake an emulator system-image download error for an app crash.
- CI maintenance backlog: GitHub Actions warns about the forced Node.js 24 transition, deprecated `actions/setup-java@v4` (migrate to v5 when testing), and `ubuntu-latest` moving to Ubuntu 26 starting October 19, 2026. Audit workflows during release hardening; do not treat these warnings as current Kotlin test failures.
- Stage work in coherent, roughly 20–30 minute batches, give real progress checkpoints, push tested commits, and update this file. Never promise unseen background work.

## Latest verified release and changes

- Latest published signed beta at start of this finishing run: **v0.13.10**, release https://github.com/coolchan03/Monogatari/releases/tag/v0.13.10-signed-20261015. It has the in-app podcast/video RSS player, caption sidecars, chapter seeking, and a simple Kitsune icon from earlier versions. It is not official v1.0.0.
- PR #8: Standard Ebooks full public search and publisher-listed compatible EPUBs; Twitch public clip discovery. **MERGED** https://github.com/coolchan03/Monogatari/pull/8
- PR #9: Public-provider/URL validation and QA reliability. **MERGED** https://github.com/coolchan03/Monogatari/pull/9
- PR #10: JavaScript extension permissions and health disclosure. **MERGED**, full CI succeeded on head `d083f0e`, merge `35270b9`: https://github.com/coolchan03/Monogatari/pull/10
- PR #11: Global public-book publication-year/unknown-date filtering and Plex structured year fix. **MERGED** on October 15 (local time): tested head `1caf84f`, merge `fa5271e`. https://github.com/coolchan03/Monogatari/pull/11 . Exact-commit GitHub Actions **SUCCESS** (unit tests, debug APK, unsigned R8 and Android 15 cold start): https://github.com/coolchan03/Monogatari/actions/runs/38833241221 . Version in code: `0.13.12`; a signed 0.13.12 APK has **not** been released.
- Publication-year batch details and regression tests: `docs/BATCH_PUBLICATION_YEARS_20261015.md`, `PublicBookYearSorterTest.kt`, `PlexRepositoryTest.kt`. Structured years are distinct from manga title-derived estimates.

## Implementation counts (do not conflate with real-device acceptance)

After PR #11 merged, published `devel` has **69 DONE, 14 PARTIAL, 0 PENDING** out of 83 Android items. These are code implementation statuses, **not** proof of two-device functionality. The original five Windows milestones are removed, not “partial.”

## The 14 still-partial items — finish in the following order

| # | Gap and concrete next step |
|---|---|
| 11 | Unify the toolbar/navigation/card system across Settings, More, Books, Media, news and specialized destinations; test Back/rotation. |
| 14 | Books catalog: add reliable series/edition metadata, format availability, borrowing notices and consistent direct open/download actions; retain valid covers. |
| 17 | Extend loading skeletons, empty states, error explanations and query/state restoration to every global search and specialized screen, not only Books. |
| 18 | Global typography, true dark mode, edge-to-edge navigation and screen-reader descriptions; avoid tiny/clipped elements. |
| 21 | Actual speech-bubble-shape segmentation instead of merging only rectangular OCR regions; test CJK and RTL boundaries. |
| 23 | Bubble-constrained translated text layout including vertical Japanese, font choice and safe text fitting without cutting art. |
| 32 | Complete interoperable genre/demographic, language, direction, format and progress filters for all media types; hide unsupported filter widgets. |
| 37 | Cross-source duplicate-title grouping and **per-title preferred manga source**, while keeping failed or mismatched copies distinct. |
| 45 | StoryWeaver: currently official-site search; native metadata + illustrated-reader integration requires compatible authorized endpoints. Never bypass access limits. |
| 48 | FictionPress and FanFiction.net: official-site discovery only; CAPTCHA/bot barriers block reliable native metadata parsing. Respect provider rules; if impossible, obtain explicit scope decision instead of claiming native integration. |
| 58 | Plex server account/token, recent/search, episodes and direct playback exist. Verify the private-server/codec/stream path via authorized credentials and physical devices, and handle failure states gracefully. |
| 63 | Complete true two-pane library/search/detail workflows at tablet widths and practical three-column layout, including pane selection. |
| 66 | Full foldable/rotation/freeform multiwindow state restoration and window insets checks on the phone/tablet; test posture changes. |
| 69 | Accessibility audit: system 200% font scale, TalkBack labels and focus order, contrast, keyboard handling and >=48dp targets throughout. |

## Next batch instructions

1. PR #11 is already merged and green. Verify the latest live `devel` and outstanding PRs before choosing a new task; do not reimplement publication-year sorting. The next work should address the 14 PARTIAL items below.
2. Keep this handoff and the repository README updated after each completed batch so a new chat never resumes outdated code or redoes already merged work.
3. Work on #11, #14, #17, #18 together as a design/error-state batch; then #32 and #37 filters/duplicate-source semantics; then #21/#23 OCR; finally media-provider work (#45/#48/#58) and adaptive/accessibility (#63/#66/#69).
4. After the last item truly meets its code specification, run a full code audit, unit/instrumented tests, signing dry run, security/source/privacy review, all media entrypoints, APK cold start and a signed **v1.0.0** release. Verify exact-commit/tree and SHA-256 on published artifact. Do not require owner to do routine developer QA; physical test occurs **after** that release.
5. The owner will test v1.0.0 on a **Moto Razr Ultra 2025** and **onn Pro 13.2-inch tablet**. Provide the user with a first-run guided survey using app-area highlighting and copy-to-clipboard diagnostic report. Test both manual Nearby peer sync of library/settings and file transfers with matching pairing code, unified and per-media searches, favorites/history/book/video/audio, source scanning, CAPTCHAs, offline books, and large-screen UI.

## Important product requirements to preserve

- Monogatari is **entertainment only**: manga/manhwa/yaoi/webtoons, novels, anime/video, audio/podcasts and related news. No education-focused features.
- Combine the Kotatsu-Redo **one global search, built-in parsers, hide broken/empty results, CAPTCHA solving, search year/date** with Mangayomi's **extensible first-class JavaScript/custom sources and broader media**. Preserve working Kotatsu built-ins.
- Offer theme presets reminiscent of Kotatsu and Mangayomi, but keep Monogatari's own cleaner identity and simple Kitsune fox/book icon. Avoid reused, pixelated or generic file-type thumbnails—especially Internet Archive audio.
- More and Books should be visually consistent with Settings; Source Test Center should keep the screen awake during active scan, handle several sources concurrently, and return a clear, copyable diagnostic report. Don't spam unsuccessful scans as “updates.”
- Nearby peer sync should require confirmation on both devices, work offline without silently overwriting data, transfer library/settings and separately chosen files, use integrity checks, and stop background discovery.
- Downloaded manga chapter caches are not assumed to synchronize automatically; don't claim otherwise. Treat protected and geo-blocked provider content respectfully.

## How to start the next chat

“Please read `docs/MONOGATARI_NEXT_CHAT_HANDOFF.md` and `docs/ROADMAP_83_TRACKER.md` from `coolchan03/Monogatari`. Begin by requesting Tunnel to Surface permission. Verify live `devel`, open PRs, checkout status and latest CI. Continue completing the remaining PARTIAL Android roadmap items in 20–30 minute tested batches; push all GitHub writes via the tunnel. No Android SDK or emulator installation on my devices. Publish the first official signed v1.0.0 only when all in-scope functionality is implemented in code, then I will test it on two devices.”

## 2026-10-16 supplemental code handoff

Latest finishing branch: `finish-83-20261015` in `coolchan03/Monogatari`, isolated worktree `C:/Users/coolc/Monogatari-Finish83-20261015`. Do not reset the original dirty P: checkout. See `docs/CODE_FINISHING_14.md` for the fourteen target item implementations. Early pushed commits: `a4605f1`, `612f4f3`, `6370587`, `d58e2e5`, `a3081b8`.

User specifically instructed: **feature verification must be the very last stage**. Write Android code and push via Tunnel to Surface first; treat GitHub compilation checks as programming feedback only and do not claim feature validation from them. Do not merge into `devel`, publish a signed version, or mark all 14 DONE before final full test/owner acceptance. There is no Windows project.

### October 16 follow-up development batch
Draft [PR #13](https://github.com/coolchan03/Monogatari/pull/13) is the exclusive integration route. Next code work: authorized offline EPUB import with format validation, accurate global metadata filtering, tablet preview and library state restoration, official-fiction HTTP error handling, and new GitHub-hosted **compile-only** workflow (`.github/workflows/code-compile.yml`). Preserve the distinction between compilation and full feature verification. Keep tracker statuses PARTIAL until the actual last-stage acceptance.

### October 16 next code batch: global Books completeness
Branch `finish-83-20261015`, draft PR #13. Shared `PublicCatalogProvider` registry covers all eleven Books providers in global search; concurrent results stream progressively while failed providers get an informational row. Gutenberg/Open Library/Google Books subjects power book genre filtering; global results retain official free EPUB URLs and provide in-app offline save/read choices (including tablet preview). This code is **not final-feature-tested**. The compile-only GitHub workflow is allowed; defer provider/device verification, merge, signing and release until the final stage. Details: `docs/CODE_FINISHING_14.md`.

## 2026-10-18 Claude cloud session: v1.0 finishing batch 1

- **Push route changed for this session:** no Tunnel to Surface tool exists in the Claude cloud container. With the owner's explicit approval, commits are pushed with this container's own git remote to `claude/monogatari-v1-release-sq1t2k`. The GitHub connector stays read-only. No merge, release or `devel`/`finish-83` write. The owner declined a local Android SDK in the container, so all compilation and tests run in GitHub Actions (`build-check.yml` runs on `claude/**` pushes).
- The branch started at finish-83 head `a3837b2` (draft PR #13, no newer work found). Baseline `build-check` on `a3837b2`: unit tests and debug APK passed (run 39176557261).
- Batch 1 code: source-failure triage (`docs/SOURCE_DIAGNOSTICS_20261018.md`), per-source Retry / Solve CAPTCHA in global search (#17 + CAPTCHA handling), TalkBack labels on icon-only buttons (#69), future roadmap `docs/FUTURE_VERSION_ROADMAP.md`.
- Batch 1b: Plex error explanations (#58), source-neutral player error text, safe external links for untrusted URLs.
- Baseline `build` job on `a3837b2` passed (unit tests, debug APK, R8 release).
- Head `b303510` CI green (run 39177526228: unit tests, debug APK, R8, cold start). Compilation and smoke tests only, not feature verification.
- Next batch: #21/#23 OCR refinement review, #32/#37 filter and duplicate semantics, #63/#66 adaptive state, then the full audit.
- The 14 PARTIAL rows remain PARTIAL. Code presence ≠ verification.

### 2026-10-19 batch 2
Bubble-shape text fitting (#21), vertical CJK punctuation (#23), source content-type format filter (#32), reflow-safe reading position (#66), EPUB download retries (#14), TalkBack labels (#63/#69). Details: `CODE_FINISHING_14.md`. Tracker rows still PARTIAL until final verification.

## 2026-10-23 — v1.4.1 released (public)
- https://github.com/coolchan03/Monogatari/releases/tag/v1.4.1 built from `devel` `54f2209` (drafts merged; build check run 39978458243 green; release started by one owner-approved connector workflow dispatch; not draft, not prerelease). APK SHA-256 `162e81a8fa66317518080bae57c533b07f16baec654d4fabe16a64999ccafd1f`.
- Library covers every medium (On this device section, Recommended strip), Library replaces On device on the default bar (one-time migration, pref `nav_layout_v3`), Discover opens on All. Not device-verified yet.

## 2026-10-23 — v1.4.0 released (public)
- https://github.com/coolchan03/Monogatari/releases/tag/v1.4.0 built from `devel` `7ff9186` (drafts merged; build check run 39957411594 green; release started by one owner-approved connector workflow dispatch; not draft, not prerelease; first release using the commit-recording check from the v1.4 audit R01 fix). APK SHA-256 `c5d27076350b9a90382c61bdf70e166868606b1f706d9c9e7ba772a9bdd1b673`.
- Includes Settings groups, rebuilt Library, AniList/MyAnimeList fallback, opt-in auto-download, themed screens + mascot, and the v1.4 audit fixes (docs/MONOGATARI_V1_4_AUDIT_FINDINGS_20261022.md). Not device-verified yet; open items are listed in docs/FUTURE_VERSION_ROADMAP.md.

## 2026-10-21 — v1.3.0 released (public)
- https://github.com/coolchan03/Monogatari/releases/tag/v1.3.0 built from `devel` `0f7cb5f` (drafts merged; build check run 39525327963 green; release started by one owner-approved connector workflow dispatch; not draft, not prerelease). APK SHA-256 `fb79ac670109f95fd9557831f5777cfe5175a08ef83df8f513b68c92ceae66af`.
- Includes Add files, Add to library from Discover, 8-item navigation, Mangayomi extension/library import, shelf timeout fix. Not device-verified yet.

## 2026-10-21 — post-1.2.0 batch (drafts branch `d1d743b`, CI run 39517947734 green, unreleased)
- Nav bar: up to 8 items (floating pill compacts/scrolls; overflow listed in More); new tabs News & RSS, Podcasts, All media, Book catalog (fragments with thin activity hosts); shortcuts Search, Downloads, Statistics, Sources, Settings.
- Mangayomi import matched to the current format (.backup zip with .backup.db JSON v2): installs every installed JS extension (repos, else backup sourceCode with the repo checks, else sourceCodeUrl), adds https extension repos, imports manga/anime/novel library even without the extension, large backups (base64 APKs) no longer rejected, encrypted backups explained.
- Discover: long-press > Add to library (built-in titles directly; AniList titles via exact-title lookup on enabled sources of that type).
- Fix: timeouts inside runCatchingCancellable cancelled whole Discover shelves / left Books-Videos shelves spinning; now withTimeoutOrNull.
- Status: implemented, CI green; not device-verified; real Mangayomi backup not tested (owner's file >30 MB).

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

## 2026-10-20 — v1.1.0 released
- https://github.com/coolchan03/Monogatari/releases/tag/v1.1.0 built from `devel` `3d3e25a` (drafts branch merged; build check run 39349438710 green; tag push blocked by proxy, release started by one owner-approved connector workflow dispatch). APK SHA-256 `f1b102fdefd3af9322ccd5febaedd7617a108264d7d1a82e20c92cc109308662`.
- Implemented and CI/unit-tested, NOT device-verified: Mihon import, Nearby two-way sync with settings groups, Discover "For you", smart collections, side-by-side translation, player auto-next, EPUB read-aloud.
- Audit note (2026-10-20): style grep found fixed 15sp text in OpenSourceLicensesActivity and ForeignBackupReviewActivity; no screen has been visually checked (no screenshots yet). Next proposed: CI screenshot pass (light/dark), tablet/Razr layout check, real .tachibk import test.

## 2026-10-19 — v1.0.1 released
- https://github.com/coolchan03/Monogatari/releases/tag/v1.0.1 built from `devel` `1d06598` (build check run 39330141224 green; release started by one owner-approved connector workflow dispatch). APK SHA-256 `831005d4ce7744689f8629011f6bb6c04019d9ef913d178d76e34f155b4714ca`.
- Contents: owner feedback fixes, full-resolution Kitsune art, consistent screens (see `docs/RELEASE_1_0_1_NOTES.md`). v1.1+ drafts stay on `claude/monogatari-v1-release-sq1t2k`, which has `devel` merged back in.
- Next: owner device test with the Test Center; 1.0.2 candidates: move hard-coded screen text to string resources, update deprecated GitHub Actions (checkout/upload-artifact/setup-gradle v4).
