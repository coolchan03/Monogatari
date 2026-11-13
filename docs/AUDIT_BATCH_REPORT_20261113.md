# Audit implementation report - 2026-11-13

Base: `devel` `4ce6770ee16d5fbd08da604a279beabc43986487` (v1.9.0), the newest `devel` when work started. Branch `ccr-206cf2cb-3oxtfw`. **No release was built, tagged or published.** Environment: cloud container (no emulator, no KVM, no physical device), so nothing below has been seen running on Android.

## Commits

| Commit | Content |
|---|---|
| `fd44501` | adaptive search panes, richer preview, format facet, in-place kind switch (UI-01..05) |
| `707b918` | Material dropdown fields replace native Spinners in search filters (UI-06) |
| `6037558` | QuickJS runtime tests in the JVM, SkyMangas/Rocks Manga flow tests, upstream review docs, upstream-watch, README |

## Tests executed

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` on v1.9.0 baseline | 964 PASS, 0 FAIL, 10 SKIP (the 10 skips: 8 `JsExtensionTest` + 2 others, because QuickJS had no desktop library) |
| `:app:testDebugUnitTest` after batch A | 975 PASS, 0 FAIL, 10 SKIP |
| `JsExtensionTest` with desktop QuickJS on the test classpath | 8 PASS, 0 SKIP (previously 8 SKIP) |
| `MaintainedExtensionsQuickJsTest` (new) | 6 PASS |
| `scripts/validate-ui-a11y.py` | PASS (updated for the new layout) |
| Final `:app:testDebugUnitTest` + `:app:assembleDebug` on the finished branch | 981 PASS, 0 FAIL, 2 SKIP; debug APK built (sha256 `7c92e84a507e6077b0e556c4f0ad902971d2eca37a2d9cf425b70a0ef4ba1ca9`, unsigned-for-release debug build, not a release artifact) |
| `:app:lintDebug` | **INCOMPLETE**: `lintAnalyzeDebug` was still running when the 55-minute limit stopped it (exit 124). Same outcome as v1.9.0; no result, not a pass. Next step: run on a larger CI runner with a lint baseline, or split by module |
| `:app:assembleRelease` (R8, version 1.10.0 / code 34) | BUILD SUCCESSFUL (16m50s). The artifact is not signed with the release key and was not published |
| Emulator / device UI (phone portrait, tablet portrait/landscape, fold, 200% font, TalkBack) | NOT RUN: no emulator available. Owner step. |

New unit tests: `SearchPaneLayoutPolicyTest` (9), `SearchContentFormatTest` (9), `SearchPreviewActionsTest` (6), `LatestSelectionLoaderTest` (4), `MaintainedExtensionsQuickJsTest` (6).

## Status by finding

| ID | Status | What was done / what is open |
|---|---|---|
| UI-01 tablet portrait stacks preview below | COMPLETED in code, device check owed | `SearchPaneLayoutPolicy` uses the live window: <600dp list only; portrait >=600dp preview **below**; landscape >=720dp x >=480dp side; short windows list only. Forced-horizontal `layout-w600dp/w840dp` copies removed. |
| UI-02 useful preview | COMPLETED in code, device check owed | cover (CoverImageView with source headers), alt title, creators, source, status, language, rating, chapters, tags, reading progress, description with Show more; lazy details, 20 s timeout, stale selections cancelled, no invented fields. Year is not shown (no reliable field). |
| UI-03 Manga/Webtoon/Comics facet | COMPLETED in code | chips Manga, Webtoons, Comics, Manhwa, Manhua + "Include format unknown", shown only for the Manga type, saved in `SavedStateHandle`. Webtoon requires an explicit tag; a manhwa source is not treated as webtoon. Limitation: `MANGA` source type is coarse (the parser contract includes webtoons in it), so the Manga chip can contain untagged webtoons from such sources. |
| UI-04 shared preview logic | PARTIAL | actions/metadata rules are pure and tested, but the Details screen's `PreviewFragment` was not refactored to share the new components (duplicated rendering remains). |
| UI-05 in-place search-kind switch | COMPLETED in code | no more activity recreation; query, filters, type and selected preview stay; results re-query (scroll resets because the results change). |
| UI-06 filter dialogs | PARTIAL | Spinners replaced by Material exposed dropdowns. Still dialogs, not a chip bottom sheet with search-as-you-type/Clear-Apply. |
| UI-07 Explore vs Discover naming | NOT STARTED | |
| UI-08 / QA-02 device matrix | BLOCKED (no device) | see checklist |
| UX-01 surface filters | PARTIAL | format chips are surfaced; other filters still in the overflow menu |
| UX-02 manga dedup | NOT STARTED beyond existing `MangaCrossSourceGrouper` | |
| SRC-01 parser bump | BLOCKED (owner decision) | `docs/UPSTREAM_REVIEW_20261113.md`: newer parsers are a signed runtime bundle with relocated contract; no JitPack build for the pinned coordinate. Pin unchanged at `68f3fed395`; no source id touched. |
| SRC-02 Kotatsu-Redo 9.8.7 | REVIEWED, NOT INTEGRATED | per-area table in the review doc |
| SRC-03 Mangayomi | REVIEWED, nothing to port | verb routing already tested; filter fix is Dart-only |
| SRC-04 / SRC-05 source proof | PARTIAL | SkyMangas and Rocks Manga now pass search->details->chapters->pages in the **real QuickJS host** with canned responses (QUICKJS FIXTURE evidence, not live). Softkomik, Temple Scan, Catharsis World, MangaCloud untouched: need legitimate parser/extension work and device/site checks. No per-source live smoke runner and no quiet status UI yet. |
| SRC-06 re-sweep | NOT RUN | owner's device |
| SRC-07 upstream-watch | COMPLETED | observed vs integrated fields, links, status |
| DOC-01 README | COMPLETED | latest release link now v1.9.0; changelog entry for this work is NOT added because nothing is released |
| QA-01 lint | INCOMPLETE (see above) | no CI step added, since an unbounded lint job would only fail the build without information; needs a baseline decision |
| ARCH-01 refactors | NOT STARTED | the audit says to refactor only after profiling; none done, no behaviour risk taken |
| ARCH-02 App Links | NOT STARTED | needs `assetlinks.json` evidence |
| ARCH-03 lint suppressions | NOT STARTED | |
| REQ-01 roadmap reconciliation | COMPLETED | note in `docs/ROADMAP_83_TRACKER.md` |

## Preserved

No Room schema change, no preference key renamed (new keys: `monogatari_search_panes` file, `format_facet`/`format_include_unclassified` saved-state keys), no source id, backup format, publisher policy or HTTPS setting touched. One test-only dependency added: `io.github.dokar3:quickjs-kt-jvm` at the app's existing QuickJS version (`libs.versions.toml` line `quickjs-kt-jvm = { module = "io.github.dokar3:quickjs-kt-jvm", version.ref = "quickjs" }`, `app/build.gradle` line `testImplementation libs.quickjs.kt.jvm`).

## Process deviations

- "Tunnel to Surface" (AGENTS.md workflow) is not a tool in this cloud session. Work was edited and committed locally; the GitHub connector was not used to write anything. Read-only clones of three upstream repositories were made for the review.

## Owner device acceptance checklist

1. Phone portrait: search shows one pane; tapping a result opens Details as before; no preview strip.
2. Tablet portrait: tap a result; preview appears **below** the list; drag the handle, or focus it and use Up/Down; TalkBack offers "Larger/Smaller preview". Rotate during loading: selection, query and filters survive, no duplicate screens.
3. Tablet landscape: preview on the right, adjustable; with nothing selected no empty right-hand slab.
4. Menu > "Preview layout": Automatic / List only / List and preview; Hide/Show preview works and persists.
5. Manga type: format chips appear; Webtoons with "Include format unknown" off hides untagged titles, on keeps them; chip row hidden for Books/Video/Audio.
6. Search kind (name/author/genre) changes without a new screen flash and keeps the preview.
7. 200% font size, split-screen, fold open/close on the preview and filter dialogs.
8. SkyMangas and Rocks Manga installed from the Sources screen: search, open, read a chapter on a real network (the site may still be down or challenge you; that is a site result, not necessarily an app bug).
