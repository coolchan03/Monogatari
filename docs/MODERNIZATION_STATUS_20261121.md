# Post-1.10.0 modernization - status (2026-11-21)

Branch `ccr-bf323bfe-6yd048`, local commits only, nothing pushed, nothing merged into `devel`, no APK published.
Status words: **DONE** (implemented and covered by the stated check), **PARTIAL**, **UNVERIFIED** (code is in, behaviour not
checked), **DEFERRED** (not started this session). Live-provider checks and device checks have not been run at all.

## Checks actually run

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` against parsers `b049587f96` | 0 errors |
| `:app:testDebugUnitTest` (parser bump, NewPipe 0.27.1, then the 1.11.0 work) | 1010 tests, 0 failures, 0 errors, 2 skipped |
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

## 4-5. Mangayomi / Kotatsu-Redo app improvements (updated for 1.11.0)

Checks run for these: 1010 unit tests pass (14 new for chapter gaps, 6 for genre labels), Room validated the new SQL at compile time, UI static checks and the R8 shrink pass. **None of the UI changes were seen on a device.**

| Item | Status |
|---|---|
| Missing-chapter detection (P2) | DONE (logic, 14 tests) + shown as a header line on the chapters tab; hidden while searching / downloaded-only. "Warn before jumping across gaps" NOT done |
| Preserve reading statistics during source migration (P1) | DONE in `MigrateUseCase` (`StatsDao.moveSessions`); not run against a real database |
| Preserve custom cover / title / reader mode / colour filter on migration | DONE (per-title prefs copied unless the target already has some); not run against a real database |
| Avoid Continue Reading when nothing is left (P2) | DONE for the last-read title being finished (history percent); not seen on a device |
| Restore last selected library category (P2) | DONE (new pref `last_favourites_category`); not seen on a device |
| Recommendations "Not interested" + Undo (P2-P3) | DONE, local only (not uploaded, not in backups); not seen on a device |
| Invalid website addresses | ALREADY PRESENT: `SourceSettings` validates a stored domain and falls back to the default |
| Source migration: limited parallel searches, failed sources not blocking, keeps favourites/history/tracking/scrobbling | ALREADY PRESENT (semaphore-limited `AlternativesUseCase`, `MigrateUseCase`); read, not exercised |
| Migrate titles off a retired source | ALREADY REACHABLE: opening such a title raises the unsupported-source error with Alternatives offered; migration falls back to the cached manga when the old source cannot load |
| Reader: blocking I/O on the main thread | AUDITED, no clear violation found (prefetch and page load run on Default / worker threads); not exhaustive, no profiling |
| Download queue persistence, move to top/bottom, whole-series moves | NOT DONE: downloads are independent WorkManager jobs; needs a new queue model and device testing |
| Lock-screen/state restoration, navigation-bar insets, next-page preparation | NOT DONE (need a device) |
| Library: floating-controls overlap, faster category editing, swipe read-status, extension filter/repo recheck, stale extension listings | NOT DONE |
| Source-migration UX (progressive dialog, long titles, confirm uncertain matches) | NOT DONE |
| Device sync via QR / LAN / Tailscale | NOT DONE (not started; extend the existing Nearby transfer if pursued) |
| GIF/WebP playback in the reader; sync reconciliation; deleted favourites reappearing | NOT DONE |
| Genre-label normalization for recommendations (P2) | DONE (`GenreLabels`, 6 tests): Sci-Fi / Science Fiction, Shonen / Shounen, Humor / Comedy etc. match in suggestions and in the tag blacklist; related genres such as Shounen vs Shounen Ai stay apart |
| Explore grid size, hidden-image/repetitive-page controls | NOT DONE |
| Section 7: removed repositories vs installed JS sources / stale listings | ALREADY SATISFIED by design: listings are fetched live from the stored repo URLs (no listing cache), removing a repo drops its listings and never touches installed source code; publisher pinning (`JsPublisherPolicy`) unchanged. Read, not exercised |
| Sync: deleted favourites reappearing, concurrent changes | NOT CHANGED: sync already uses `deleted_at` tombstones and a conflict planner; no defect found by reading; a real fix needs a sync server to test against |
| Reduce Motion (P3) | NOT DONE: no custom animator code added; platform animator-duration scale applies, not verified |
| Mangayomi P3 ideas (Reduce Motion etc.) | NOT DONE |

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

## Update 2026-11-22 (after 1.11.0): live checks, upstream port, import safety

- **Live checks (opt-in `livetest/`, counts only, datacenter network, no WebView):** list -> details -> chapters -> pages OK for MangaPark, BatoTo (XBatCat), Naver Webtoon, Comizy.io, Shinigami, SoulScans, AinzScans, LegionScans, KomikLokal, HoneyManga, Ikiru, Raw1001, MangaKatana, MangaPill, MangaKio, AtsuMoe, MangaTaro, PariteHaber, MangaGo, MangaGG, VyManga, ManhuaHot. JS host: Weeb Central and MangaDex extensions OK.
- **Failures:** Softkomik (list ok; details/pages 404 on `/api/session/...`, the site/API change the Keiyoushi item describes; upstream parser HEAD is still `b049587f96` so no newer fix exists; porting its encrypted Next.js + bearer-session flow is a large separate job). MangaDotNet: numeric chapter `id` read with `getString` (present in both the old and new parser, so not a regression; needs an upstream fix).
- **Inconclusive:** VoraToon and MerlinScans (HTTP 403 without browser verification), ManhuaScan (proxy 502), parser MangaDex (non-JSON reply).
- **Ported from upstream:** Kotatsu-Redo sync merge fix (`isRemoteCopyNewer`, `takeNewer`, `unchanged_since` guard) with its 6 tests. Server round trip not run.
- **Import safety:** `MANGAYOMI_*` fallback source names now resolve to `MissingExtensionSource` instead of `UnknownMangaSource`, so re-saving a title can no longer overwrite its stored source name.
- **Real library backup (structure only, not stored in the repo):** no favourite or history entry used a retired parser id; three retired sources were enabled in the source list and are skipped silently; most favourites came from a Mangayomi import whose extensions are not published in any known repository (the matching extension sites were unreachable or parked), so they can only be moved via Alternatives.
- **Release channel:** the publish workflow gained a `prerelease` input; 1.11.1-rc1 is published as a prerelease.
