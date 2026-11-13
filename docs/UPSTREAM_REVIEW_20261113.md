# Upstream review - 2026-11-13

Method: shallow read-only clones of the three upstreams (not Monogatari code) and `git diff`/`git log` against the revisions Monogatari builds from. Nothing here was run on a device. Status words: **INTEGRATED**, **NOT INTEGRATED (reason)**, **BLOCKED (needs owner decision)**.

## Kotatsu-Redo parser library: BLOCKED (owner decision)

- Monogatari pin: `68f3fed395` (`gradle/libs.versions.toml`, JitPack coordinate `com.github.clquwu:kotatsu-parsers-redo`).
- Upstream `master`: `c429dceb0a8232dbffe150c6b94ad95fd7ef544a`, 9 commits ahead, 109 changed paths (6785 insertions, 760 deletions).
- The first commit after the pin (`3432a85`, "Parsers that update without an app release, and source repairs") mixes ~19 site repairs with an architecture change:
  - the contract classes (`MangaLoaderContext`, `MangaParser`, all of `model/*`, `config/*`, `exception/*`, `webview/*`) are **deleted from this artifact** and tracked as a versioned API file (`api/parsers-api-v1.txt`);
  - parsers are published as a **signed, runtime-downloaded bundle** (`docs/BUNDLE.md`, `ParsersBundleImpl`, KSP processor changes, dex/signing build tasks).
- Upstream's app (9.8.7) consumes this with `PARSERS_BUNDLE_URL` / `PARSERS_BUNDLE_KEY`; its own build comment says whoever holds the private key "can run code in every installed copy of this app".
- JitPack has a build for `68f3fed395` under `com.github.clquwu` but **none** for any newer commit, so a hash bump would not resolve.
- Why not bumped: a bump cannot be a one-line change; adopting it means either vendoring the new contract module or accepting remote code execution from a third-party signing key, which conflicts with this project's extension-security rule (no silent publisher swaps). The site repairs in that range are not separable from the architecture change.
- Needed from the owner: choose (a) stay on `68f3fed395` and track repairs as Monogatari-maintained JS extensions (current approach for SkyMangas/Rocks Manga), (b) adopt the bundle with Monogatari's own signing key and an explicit opt-in, or (c) vendor the new contract and build the parsers in-tree. Saved-library source ids must be preserved in all three; the 1385 enum ids at the pin are the baseline.

## Kotatsu-Redo app 9.8.7 (observed `9ef7077`, 2026-11-13): NOT INTEGRATED

`d33b50b` (9.8.6) -> `9ef7077` (9.8.7): 146 files, 8336 insertions. Per area:

| Area | Finding | Status |
|---|---|---|
| Signed runtime parser bundle (`core/parser/bundle/*`) | remote code loading, see above | BLOCKED (owner decision) |
| Community dismissal uploader (`sourcescore/domain/DismissalUploader.kt`, `DismissalConsent`) | sends user dismissals to a community API | NOT INTEGRATED: privacy-sensitive; would need consent design |
| Discover/suggestions "Not interested" + Undo (`discover/data/Dismissed*`, `SuggestionsFragment`) | the local store is separable from the uploader | NOT INTEGRATED yet: implementable as local-only; tracked as a follow-up |
| Reader animated GIF/WebP (`PageAnimationPlayer.kt`, `AnimatedImageSniffer`) | Monogatari already has `isAnimatedImage` for covers; reader playback absent | NOT INTEGRATED: needs reader regression tests on device |
| Sync merge fixes (`SyncHelper`, `SyncMergeTest`) | Monogatari's sync differs; needs side-by-side read | NOT INTEGRATED: not yet analysed |
| Settings toggle header contrast | small theme fix | NOT INTEGRATED: low value, Monogatari uses its own settings layout |

## Mangayomi (observed `45730ec`, v0.9.95 + 29 commits): no Kotlin port needed

| Upstream change | Monogatari state | Evidence |
|---|---|---|
| `fix(js): send PUT, DELETE and PATCH from the extension client` (e7fab3b) | already correct | `JsHttpShimMethodsTest` |
| `fix(filters): skip malformed filter options instead of dropping the list` (90927ff) | not applicable: this is Dart-side parsing of `getFilterList()`; Monogatari passes the filter JSON through (`JsExtension.getFilterList`) and has no native filter builder | grep of `jsext/` |
| `feat(logs): name the source in dalvik fetch errors` (33680ec) | Dalvik is the Flutter runtime; Monogatari has its own source log | n/a |
| migration batching, library category persistence, sync cursor writes | Flutter app features, no JS bridge surface | n/a |
| Flutter 3.50 / material_ui move | not a dependency of this app | n/a |
