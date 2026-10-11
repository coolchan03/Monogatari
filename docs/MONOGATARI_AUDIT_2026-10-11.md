# Monogatari pre-device audit — 2026-10-11

## Scope and evidence

Reviewed development branch `claude/mangayomi-kotatsu-features-fb46y2` at
`042d73506b643e93e31fa0495483743ca5889626`.
Evidence: code inspection, Android manifest/build configuration, UI/navigation,
source extension bridge, CI results, repository docs, and the user's supplied
9-source diagnostics. This is a *pre-device static audit*, **not** a guarantee
of no bugs or functional parity with either entire upstream application.

Latest full GitHub Actions `Build check` run `38181098357` succeeded:
`testDebugUnitTest`, `assembleDebug`, APK upload. No device smoke test,
release shrinker/R8 check, UI screenshot review, instrumented tests, or
comprehensive extension compatibility suite was confirmed.

## 1. Feature coverage

| Requirement | Evidence | Verdict |
|---|---|---|
| Kotatsu built-in manga sources | Parser-Redo dependency; original parser, explore, catalog remain | Implemented in code; runtime regressions untested |
| One global search | `search/ui/multi/SearchViewModel.kt` | Implemented; broad cross-media results unverified |
| Hide empty/broken search sources | Search handles empty results and broken parser sources; optional empty filtering | Partial: other errors may still appear |
| Search by year/date | `SearchViewModel` year range; excludes unknown-year rows | Partial: sources without year metadata cannot fully participate |
| CAPTCHA and Cloudflare | Original solver; JS transport now integrates coordinator | Implemented; practical Cloudflare challenge results unverified |
| Mangayomi JS source installation | `jsext/repo` store/index/parser, Source Repositories UI | Implemented; not all extensions compatible |
| Books and novels | Local Books tab; EPUB/PDF/TXT/HTML readers; OPDS/public catalog | Partial; standalone book shelf not unified with manga library |
| Anime sources/player | JS item type, video extractor bridge, Media3 reader | Partial; many source parsers and stream hosts unsupported |
| Custom bottom navigation | `NavItem`, `AppSettings.mainNavItems`, nav configuration VM | Implemented |
| Source preferences/remove/update | Source Repositories source settings UI | Implemented; end-to-end update tests needed |
| History, bookmarks, favorites, downloads | Preserved Kotatsu modules | Present; cross-media behavior needs tests |
| Sync | Inherited Kotatsu sync modules in manifest | Cross-media sync not verified |
| Custom lists/recommendations | Kotatsu suggestions, favorites/categories | Partial relative to Mangayomi collections feature request |
| Offline media | Manga downloads inherited; books local import | Partial; verified anime/offline unified download support missing |

## 2. Critical findings

**P0: No production-signed release or stable upgrade identity.**
`app/build.gradle` still declares `applicationId 'io.github.kotatsuredo.kotatsu'`
and a `.debug` suffix in the debug build. The app label/icon say Monogatari,
but release identity and verified app-link host still point to Kotatsu-Redo.
Changing the application ID *now* would create a separate app and require
migration planning. Do not silently change it before device validation.
Release keystore secrets are not configured. Production release workflow only
exists on the development branch; it requires a default-branch integration.

**P0: JS source compatibility fails in the supplied sample.** User-provided
9-source diagnostics: 1 PASS, 2 PARTIAL, 6 FAIL. Cloudflare, TLS, timeouts,
parser exceptions, and one stale installer URL were reported. Patches
`2d501a903` and `042d73506` require repeated device diagnostics; a green
compile is not evidence that these sources now work. PASS samples do not
verify actual playback.

**P0: No demonstrated full end-to-end acceptance matrix.** Device runs need
manga read, novel/EPUB, anime playback, source install/update/remove,
CAPTCHA, sync, downloads, upgrade, and data-preservation scenarios.

## 3. Design, identity and customization

**What exists:** Monogatari app label, launcher image, banner, custom Books
navigation, configurable navigation order/visibility, themes, colors,
reading/display options, source preferences, and media-type filtering.

**What is unfinished:** The base theme/styles are still named Kotatsu.
`core/media/BookLibraryFragment.kt` builds its screen from raw LinearLayouts,
Buttons and EditTexts with hardcoded English labels and fixed pixel padding;
it does not match the more polished Material UI inherited for manga. Several
visible `strings.xml` entries still say Kotatsu or Kotatsu-Redo, including
password protection, outdated-app messaging and community descriptions.
Keep genuine upstream credits, but correct *user-facing product identity*.

**Recommendation:** A consistent Monogatari visual system layered over
Material 3 (not a full UI rewrite): establish semantic theme tokens and shared
book/video library cards, unify header, navigation and empty states, and migrate
hardcoded Books strings into localized resources. Verify light/dark and tablet
layouts with screenshots during device testing.

## 4. Architecture and code reuse

**Good reuse:** existing Kotatsu reader/navigation/download/backup/CAPTCHA,
`kotatsu-parsers-redo`, Media3, and JavaScript shims derived from upstream
Mangayomi. Do not replace established parser, network or player modules
with parallel implementations without a testable compatibility requirement.

**Code-quality improvements:**
- Reduce duplicated `BookLibraryActivity` versus `BookLibraryFragment`
  logic by extracting a shared repository / ViewModel.
- Replace synthetic media pages and ad hoc preference storage where a shared
  media library abstraction is warranted, with explicit migration tests.
- Integrate JS source diagnostics as a maintained test/developer tool rather
  than temporary visible production UI.
- Cover HTTP method dispatch, JS source URL repair, crash/cancellation
  propagation, pagination, search deduplication and manifest identity with
  regression tests.
- Audit upstream versions/licenses before porting code; preserve notices and
  attribute copied logic. Do not blindly copy entire upstream projects.

## 5. Suggested next engineering sequence

1. Keep known-good public testing APK; protect user data and IDs.
2. Stabilize JavaScript extension networking (TLS/CAPTCHA) and inspect
   Anikoto/AnimeParadise scripts with source-specific fixture tests.
3. Replace ad hoc Books UI with native Material shared-library components
   (using existing project UI patterns), including localization.
4. Audit reader/save/restore paths and cross-media history/favorites.
5. Exercise search, all content modes, OPDS, source lifecycle, and player
   playback on the user's Android phone/tablet.
6. Fix device-found regressions and run unit, lint, release R8, and UI tests.
7. Resolve signing, app identity, upgrade compatibility, and public release.
8. Add further Mangayomi extractors/formats/collections/sync as validated needs.

## 6. Audit acceptance criterion

Do not certify **"all features of Kotatsu and Mangayomi"**, **"bug-free"** or
**"fully original design"** until each upstream feature has an inventory and
tested acceptance result. Current honest verdict: **functional hybrid in active
development; code builds, significant feature and runtime gaps remain**.
