# Regression test results

All runs: `./gradlew :app:testDebugUnitTest --console=plain --max-workers=2` with `ANDROID_HOME=/home/user/android-sdk`, JDK from the container, Gradle 9.0.0.

| Date | Tree | Result |
|---|---|---|
| 2026-11-10 | v1.8.1 merged into branch, parsers `b3c0000245`, OkHttp 5.3.2, NewPipe 0.26.5 | PASS: 891 tests, 10 skipped, 0 failures (baseline) |
| 2026-11-10 | + parsers `68f3fed395` | PASS (`:app:compileDebugKotlin` clean, unit tests green) |
| 2026-11-10 | + OkHttp 5.5.1 | PASS |
| 2026-11-10 | + NewPipeExtractor v0.27.0 | PASS |
| 2026-11-10 | + JsPublisherPolicy and 13 new tests | PASS: 904 tests, 0 failures |
| 2026-11-10 | + LogRedactor (10 tests), workflow hardening | PASS: 914 tests, 0 failures; `:app:assembleDebug` OK (82 MB debug APK) |
| 2026-11-10 | + publisher-change UI | PASS: tests + `assembleDebug` |
| 2026-11-10 | + RetryAfterParser (5 tests), download backoff | PASS: 919 tests, 0 failures; `assembleDebug` OK |
| 2026-11-10 | + HostMatcher (5), PrefetchTracker (5) | PASS: 929 tests, 0 failures; `assembleDebug` OK |
| 2026-11-10 | + source search timeout (5), ChapterNavigator (8) | PASS: 942 tests, 0 failures; `assembleDebug` OK |
| 2026-11-10 | + Mihon Local-source summary (3 tests) | PASS: 945 tests, 0 failures; `assembleDebug` OK |
| 2026-11-10 | + publisher offer when own publisher still lists source (2 tests) | PASS (full suite) + `assembleDebug` OK |

Lint: `:app:lintDebug` was started twice (a 580 s run, then a run left going for 40+ minutes with 2 workers); it never got past `lintAnalyzeDebug` and was stopped. NO lint result exists. Needs a longer window or CI.

Not run: lint results, `assembleRelease`/R8, instrumentation tests, cold-start smoke test, any live-site or playback check, any performance measurement. Compiler warnings were not compared with the baseline.
