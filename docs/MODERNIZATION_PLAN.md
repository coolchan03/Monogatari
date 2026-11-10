# Modernization plan (started 2026-11-10)

Branch: `ccr-c0c4a160-e5p0d2` (local commits only; nothing pushed, merged remotely or released).
Status vocabulary: NOT STARTED, IN PROGRESS, IMPLEMENTED, TESTED, BLOCKED, DEFERRED FOR HUMAN REVIEW.
"TESTED" means automated unit tests, not device verification. Update this file as work lands.

| # | Work item | Upstream | Status | Evidence / remaining |
|---|---|---|---|---|
| 1.2 | Bring v1.8.1 security fixes into the branch | Monogatari v1.8.1 `57cd30b0a` | TESTED | Verified `devel` still at `773883694`, v1.8.1 not an ancestor. Merged with a merge commit (`a30cf50f0`), history intact. Release changes (HTTP repo opt-in, redirect/downgrade checks, import limits, OPDS, backup preview) arrive as released; 891 baseline tests green after merge. |
| 2.1 | Parser library upgrade | kotatsu-parsers-redo `68f3fed395` (master HEAD, JitPack build "ok") | TESTED | Bumped from `b3c0000245`. Compiles with zero source changes; 891 tests pass. Enum diff of `MangaParserSource`: 0 removed, 15 added (ATHREASCANS CGBUM HENTAIPAW KAPPABEAST KOMIKAPK MANGAHERE MANGAMORI MANHWAREAD SANASCANS TOONKOTSU TOONTOPIO TWOKINDS TWOKINDS_RU WEBNEX XCOMIC), so saved favourites/history keep their source ids. Not yet checked: per-source behaviour against live sites. |
| 2.2-2.5 | Individual site repairs (ManhuaTop, MG Komik, Mangago, Kagane, Comix, ...) | parser library | NOT STARTED | Fixes live in the parser library, not in this repo. Needs comparison of the 149-commit range against the app's own overrides, plus live checks. |
| 3.x | Reader/decoding/download-slot/history/cover work from Kotatsu | Kotatsu-Redo | NOT STARTED | Needs reading the app's reader and `core/image` code first. |
| 4.x | Mangayomi reliability (memory, downloads, backup formats, navigation) | Mangayomi v0.9.95 | NOT STARTED | |
| 5.1 | JS extension publisher provenance | Mangayomi `fdc74ea5` | IMPLEMENTED (domain + store + tests TESTED; UI NOT STARTED) | `jsext/repo/JsPublisherPolicy.kt`; enforced in `JsSourceStore.installValidated` so all install paths (update worker, repair, bootstrap, backup restore, Mangayomi importer, diagnostics) refuse a cross-publisher install without `approvePublisherChange=true`. `fetchAll` exposes `publisherChanges` and `conflictingIds`. 13 tests. Remaining: UI that shows the publisher change and asks for approval (nothing calls `approvePublisherChange=true` yet, so cross-publisher transfers are currently impossible, which is the safe default); update-history record; preserve-settings check on approved transfer (settings are keyed by source id, so expected to carry over, untested). Legacy installs with a blank `repoUrl` are unpinned and adopt the next updater. |
| 5.2-5.4 | JS runtime compatibility, newer source packages, networking/WebView hardening | Keiyoushi, Mangayomi | NOT STARTED | |
| 6.1 | NewPipeExtractor upgrade | v0.27.0 | IMPLEMENTED (compiles, unit tests pass) | Bumped from v0.26.5; only 3 files import the extractor and none needed changes. Playback, search, subtitles, livestream, expiry handling NOT verified; needs a device/emulator with network. |
| 6.2-6.3 | SoundCloud/Bandcamp dev fixes, startup performance | NewPipeExtractor dev | NOT STARTED | Dev-only changes must not be adopted via an unstable revision without separate evaluation. |
| 7.1 | OkHttp | 5.5.1 (latest stable) | IMPLEMENTED (compiles, unit tests pass) | Bumped from 5.3.2. TLS behaviour, Cloudflare sources and WebView interop NOT verified on a device. No TLS validation was touched. |
| 7.2 | Log/crash redaction | Kotatsu | NOT STARTED | |
| 7.3 | Release workflow hardening | | NOT STARTED | `.github/workflows/` not yet reviewed. No release will be published. |
| 8.x | Source health, errors, migration, reader, privacy, search, perf | | NOT STARTED | Existing `sourcehealth/` package should be reviewed before adding anything. |
| 9.x | Full QA, perf measurements | | IN PROGRESS | See REGRESSION_TEST_RESULTS.md. |

## Environment notes
- Work ran in a Linux cloud container, not on the P: drive; the C:/P: restriction does not apply here. Android SDK is in `/home/user/android-sdk` (outside the repo); `local.properties` is git-ignored.
- Maven Central rate-limits the egress proxy (HTTP 429); retrying with `--max-workers=2` succeeds.
- No "Tunnel to Surface" tool was available (AGENTS.md workflow), so nothing was pushed via any route.
