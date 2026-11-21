# Post-1.10.0 modernization - status (2026-11-21)

Branch `ccr-bf323bfe-6yd048`, local commits only, nothing pushed, nothing merged into `devel`, no APK published.
Status words: **DONE** (implemented and covered by the stated check), **PARTIAL**, **UNVERIFIED** (code is in, behaviour not
checked), **DEFERRED** (not started this session). Live-provider checks and device checks have not been run at all.

## Checks actually run

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` against parsers `b049587f96` | 0 errors |
| `:app:testDebugUnitTest` (after parser bump, and again after NewPipeExtractor 0.27.1) | 990 tests, 0 failures, 0 errors, 2 skipped |
| `:app:minifyReleaseWithR8` (new parser jar ships its own keep rules) | passes |
| Device / emulator run, Room migrations on a real database, backup restore, live site requests | **not run** |

## 1. Parser library `68f3fed395` -> `b049587f96` (P0)

The Nov 13 review (`UPSTREAM_REVIEW_20261113.md`) recorded this as blocked because JitPack had no build past the pin. JitPack now
serves `b049587f96` under `com.github.clquwu`. The jar still contains the contract classes *and* every parser, so it links statically:
no bundle is downloaded and no signing key is trusted.

| Item | Status | Notes |
|---|---|---|
| Upgrade the dependency | DONE | `gradle/libs.versions.toml` |
| Resolve build/API changes | DONE | `MangaParserSource` is now a class (companion `entries/find/valueOf`) extending `ParserMangaSource`; `MangaParser.source` is `ParserMangaSource`; `newLinkResolver` -> `resolveLink`; no more `EnumSet`/`ordinal` over sources |
| `MangaLoaderContext` implementation | DONE | `parsers = ParsersBundleImpl` (the built-in bundle) |
| `ParsersBundle` / `ParserMangaSource` | PARTIAL | `ParserMangaRepository.source` and the context use them. Source enumeration still goes through `MangaParserSource.entries` under an app-wide `DirectParsersAccess` opt-in (app/build.gradle). That is only valid because no runtime bundle exists; remove the opt-in and enumerate via `context.parsers.sources` if bundles are ever adopted |
| Source enumeration / instantiation | DONE (compiles, unit-tested) | `allMangaSources` keeps registry order in a `LinkedHashSet` |
| WebView integration | UNVERIFIED | `interceptWebViewRequests`, `captureWebViewUrls`, `evaluateJs` signatures unchanged and still implemented. New default `requestCloudflareVerification` is not overridden. The COMIX special case in `requestBrowserAction` is kept. Not exercised in a browser |
| Preserve existing source ids | DONE | `RetiredSourcesTest` resolves all 1385 ids that shipped in `68f3fed395` to either a live or a retired source with the same name |
| Preserve stored manga / chapters / bookmarks | PARTIAL | No table or column was touched, so rows are unchanged; not tested against a real database |
| Retain ids of removed sources | DONE | 39 ids -> `RetiredMangaSource` (see below) |
| Test migrations / backup formats | DEFERRED | `Migration16To17` now uses list position instead of `ordinal` (historical path only); no DB test exists for it |
| Dependency conflicts | PARTIAL | resolved graph has parsers `b049587f96`, OkHttp 5.5.1, NewPipe v0.27.1; compile/R8/unit tests pass. No runtime check |
| Signed parser bundles (P3) | DEFERRED, deliberately | Not enabled. Needs Monogatari-controlled keys and a security review first |

## 2. Native source repairs

Arrive with the jar. **None verified against live sites.**

- Source set: 1385 -> 1376 ids (39 removed, 30 added: `COMIZYIO`, `HIZOMANGA`, `NAVER_WEBTOON`, 27 `XCOMIC_*`).
- Newly marked working (were broken): `LEGIONSCANS` (now titled CerberusSeries), `XBATCAT` (now titled BatoTo), `MANGAPARK`, `HENTAIORIGINES`, `KLIKMANGA`, `KOMIKLOKAL`.
- `COMIX` is still broken upstream and stays off.
- **Behaviour change:** `MANGAPARK` changed content type MANGA -> HENTAI, so it is hidden when "hide adult sources" is on.
- New sources are inserted disabled unless the user already has "enable all sources" on (`assimilateNewSources`); nothing is switched on silently.
- Per-source classes for Shinigami, VoraToon, DoujinDesu, LoneSeal (SoulScans/AinzScans), LectorHentai, KomikTap, MangaSusuku, KlikManga, HentaiOrigines, Comizy, Naver Webtoon differ in the new jar, but 1521 of 4253 classes differ because of the contract refactor, so that is **not** proof a given repair landed.
- Cookie isolation, bounded retries, interactive-verification fallback: not reviewed.

## 2b. Retired and duplicate sources

| Item | Status |
|---|---|
| Do not delete favourites; keep ids in DB and backups | DONE - ids resolve to `RetiredMangaSource(name)`, no row is rewritten |
| Display unavailable sources | DONE - "<former title> (retired)" with a summary line; adult flag preserved so "hide adult" still applies. Not seen on a device |
| Cleaned-up source discovery | DONE - retired ids are not in the catalogue |
| Identify replacements | PARTIAL - only the 5 NetTruyen mirrors have hints (-> `NETTRUYEN`, `NETTRUYENX`, `NETTRUYENVIE`, `NETTRUYENHE`), by name only. The other 34 have no evidence-based successor; 34 of the 39 were already marked broken |
| User-driven migration to a replacement | DEFERRED - existing Alternatives/migration flow not yet connected to retired sources; titles are never moved automatically |
| Backup restore with retired ids | PARTIAL - id resolution tested; restore path itself not run |

Live non-broken retired sources (the ones users may have enabled): `ZINCHANMANGA_NET`, `MANGAROCK`, `FURYMANGA` (KingOfScans), `TEMPESTSCANS`, `NETTRUYENSSR/1975/UU/FE/LL`.

## 3. Keiyoushi fixes

Not implemented. HoneyManga, ZeistManga, DoujinDesu (410 refresh), Merlin Scans, Softkomik, Ikiru, Madara alternate titles and Raw1001 all exist in the new parser jar and their classes changed, but I could not read the upstream commits from this environment, so whether each fix is already included is **unknown**. Next step: fixture or live check per source, then implement only what is still missing. Comix: stays disabled pending functionality and performance checks.

## 4-5. Mangayomi / Kotatsu-Redo app improvements

DEFERRED, not started: download-queue persistence, reader responsiveness, missing-chapter detection, library and migration improvements, device sync, GIF/WebP playback, sync reconciliation, Not Interested, and the rest of those lists.

## 6. NewPipeExtractor

| Item | Status |
|---|---|
| v0.27.0 -> v0.27.1 | DONE (POM differs only by jsoup 1.23.2 -> 1.24.1; no conflict seen) |
| SoundCloud AAC 96, Bandcamp search/playback, YouTube search/playlists/subtitles/playback, stream selection | UNVERIFIED - need live network and a device |

## 7. JavaScript extensions

DEFERRED. `jsext/` untouched; its unit tests still pass.

## 8. Regression areas

Only the deterministic unit suite and the R8 build were run. Source functionality, reader, downloads, backup restore, sync, playback, tablet/phone layouts and lifecycle restoration are all outstanding.

## Other changes

- `SourceIndexHealthTest.mangayomiParserFiltersOnlyRecentConfirmedBroken` was a time bomb (fixed `now` of 2027-01-15 vs the real clock); the stale timestamp is now relative to the real clock. Not related to the parser.
- Build environment used here: Android SDK platform 37.0 and build-tools 35.0.0 installed under `/opt/android-sdk` (outside the repo).

## Suggested next steps (engineering order)

1. Review/approve the two local commits; decide when to push.
2. Backup-restore and Room-migration tests with retired ids (needs a real or fixture database).
3. Fixture tests for the P1 repaired sources, then bounded live checks, kept separate from the fixtures.
4. Connect retired sources to the Alternatives/migration flow with explicit per-title confirmation.
5. Per-source check of the Keiyoushi list against the new jar before writing any new parser code.
6. Then phases 6-8 of the recommended order (downloads, reader, migration UX, sync).
