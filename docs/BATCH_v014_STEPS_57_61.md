# Monogatari batch: Steps 57–61 — 2026-10-13

## Step 57 — Novel/subtitle translations: PARTIAL
- Added `TranslationChunker.kt`: pure paragraph-aware ~1500-character bounded splits, with regression test. Does NOT translate text, call ML Kit, rewrite VTT, add UI, or download language models.

## Step 58 — Missing JS host function diagnostics: PARTIAL
- Added `MissingJsHostDiagnostics` with thread-safe bounded per-name counts, no arguments/tokens/URLs captured. Connected to unsupported JsUtilsBridge host calls. Unit tests assert names/counts and reject sensitive URL strings.
- Not yet wired to the existing JS diagnostics UI/report or complete host-call coverage.

## Step 59 — Missing JS host functions: NOT IMPLEMENTED
- Existing unsupported functions include CryptoJS, unpackJs, parseDates, WebView JS. Reviewed JsUtilsBridge interface. Do not claim these host functions work until actual compatible ports and tests exist.

## Step 60 — Video extractors: PARTIAL
- Unsupported extractor names now recorded as `extractor:<name>` without URLs, arguments or media. This does not add another video host; existing Sendvid remains the only confirmed port in JsVideoExtractorBridge.

## Step 61 — Dart source rewrites: RESEARCH ONLY
- Inspected `DartSourceCompatibility.kt`, which matches Dart-only sources to existing site-compatible built-in or JS sources but intentionally never executes or rewrites Dart. No new source was published or added to source pools.

## Old CI regression
- Earlier CI for v0.14 `38401800333` was still in progress at start of batch. All new changes require exact-commit JVM/Debug/R8 and reader/device tests before merge or APK publication.
- No unsafe binary decryption, remote code execution, cloud translation, credentials, or model downloads were introduced.
