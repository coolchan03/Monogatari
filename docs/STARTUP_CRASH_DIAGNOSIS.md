# Android startup crash diagnosis

Monogatari uses `io.github.coolchan03.monogatari` for release and `io.github.coolchan03.monogatari.debug` for debug builds. The 4 KB reported as *app data* in Android Settings is not the APK size; a first-launch failure may leave only a tiny amount of saved data.

## Automated check

The **Build check** GitHub Action runs Android unit tests, creates APKs, and then installs the debug APK on a clean Android 15 emulator. `scripts/ci-smoke-startup.sh` launches the real activity, checks process survival, and preserves logcat and the crash buffer as `cold-start-logs`. This check does not replace real-device testing.

## Capture on your phone (without installing an SDK)

1. Connect the phone to the laptop by USB and authorize **USB debugging**. The existing `platform-tools/adb.exe` is sufficient.
2. From the repository on Windows, run `powershell -ExecutionPolicy Bypass -File scripts\capture-startup-crash.ps1` to test the installed debug app, or pass `-Variant Release` for the release app.
3. Read the generated `Desktop\Monogatari-Diagnostics\*-monogatari-crash.txt` report. Share only the relevant Monogatari exception and review it for sensitive details first.
4. If the script says there is no Java crash, collect native/process-death logs separately; the emulator test may also help reproduce it.

**Do not uninstall the existing app or clear storage just to collect logs.** Uninstalling can erase favourites and reading progress. Installing a different debug APK may require a matching signing certificate; release and debug apps have distinct package names.

## Common issue

A normal GitHub "Build check" that merely compiles Kotlin does not prove the app launches. New source, crypto, Hilt or reporting initialization can fail at runtime. Always review the *cold-start emulator smoke test* result and the real-device crash log before marking a launch crash resolved.

## Confirmed cold-start failure (2026-10-13)

Run [38467173560](https://github.com/coolchan03/Monogatari/actions/runs/38467173560) reproduced a fatal `IllegalStateException: Calling this from the main thread is prohibited` from `NetworkModule.provideBaseHttpClient(NetworkModule.kt:68)` while Hilt injected `BaseApp`. The debug-only `assertNotInMainThread()` was incorrectly placed in an OkHttp factory that Hilt may construct during normal main-thread application and activity injection.

Commit [`e2c72be`](https://github.com/coolchan03/Monogatari/commit/e2c72be15b4a9a81d3282198650918fc85e566b6) removes this invalid assertion while retaining HTTPS certificate verification and the same HTTP request implementation. The automated cold-start test remains a CI gate to catch future startup regressions.

**Verification:** Follow-up [GitHub Actions run 38469889807](https://github.com/coolchan03/Monogatari/actions/runs/38469889807) passed the unit tests, APK builds, and clean Android 15 cold-start smoke test. The [v0.13.1 test APK](https://github.com/coolchan03/Monogatari/releases/tag/v0.13.1-launch-fix-debug) comes from the verified repair commit. Device-specific behavior is not yet confirmed.
