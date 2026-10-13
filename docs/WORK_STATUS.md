# Monogatari work checkpoint

## 2026-10-13 — Startup crash diagnosis (current)

- **Canonical GitHub branch:** `devel` (only active remote branch; old tips preserved by archive tags). Do not create another development branch.
- **Previously merged head:** `a029f45eac9f143bc0293d0350a08753aa4574fd`; Claude's changes plus guarded ACRA lifecycle callbacks. CI build passed but did not launch app.
- **Repair head:** `e2c72be15b4a9a81d3282198650918fc85e566b6`; first diagnostic build `5169d2f` reproduced the crash, second build verified correction.
- **Observed issue:** User reports immediate on-device crash and ~4.10 KB of app data. APK packaging inspected: prior debug APK is ~47 MB with arm64-v8a native libraries. 4 KB is saved data, not APK size.
- **Crash reproduced:** Android 15 smoke run `38467173560` failed at `NetworkModule.provideBaseHttpClient(NetworkModule.kt:68)`: `IllegalStateException: Calling this from the main thread is prohibited`, during Hilt application injection. This is a confirmed cause of the pre-screen launch failure.
- **Fix shipped and verified:** `e2c72be15b4a9a81d3282198650918fc85e566b6` removes the invalid main-thread assertion without weakening TLS, guards ACRA-disabled paths, and bumps to v0.13.1. GitHub Actions [38469889807](https://github.com/coolchan03/Monogatari/actions/runs/38469889807) passed unit tests, debug APK build, R8 release build and a real emulator cold-start smoke test. The signed debug APK is [published here](https://github.com/coolchan03/Monogatari/releases/tag/v0.13.1-launch-fix-debug). Motorola device testing is still outstanding.
- **Device debugging:** No authorized device was attached to `adb` when checked; do not claim the phone crash has been reproduced without a log. Do not uninstall or clear data.
- **Next step:** install and test the published v0.13.1 debug APK on the Motorola device. If a device-specific crash remains, use `scripts/capture-startup-crash.ps1` to collect a targeted stack trace; never clear saved data without a backup.

## Historical checkpoint — 2026-10-12

- Date: 2026-10-12, America/Chicago
- Current branch: batch-08-10-extension-security.
- Last completed milestone: Steps 4-5 passed GitHub Actions run 38292011053 and fast-forwarded to default devel at e497d88de. Verified by git ls-remote.
- Current operation: Step 8 partial, replacing hardcoded JS source index list with BuildConfig.DEFAULT_REPOS, configured from monogatari.repos or MONOGATARI_REPOS. Existing 15 URLs set through tunnel as GitHub Actions secret (confirmed by gh secret list).
- Files edited: app/build.gradle, SourceRepoStore.kt, .github/workflows/build-check.yml, .github/workflows/publish-release.yml.
- Remaining Step 8 tasks: Implement and test monogatari://setup?repos= handler, verify workflows and Gradle compile; do not claim completion until these pass.
- Steps 9-10 not started, despite batch branch name.
- Next exact action: git diff/code QA, commit and push Step 8 partial; inspect CI. If failure, diagnose and fix before default-branch merge. Then implement deep link and scope JS extension cookie access.
- Important: Only edit/push GitHub through Tunnel to Surface. On every new turn, call Tunnel to Surface FIRST.
- Safety: Existing untracked .release-temp/ and source-audit/ left untouched. Installed old app backup/restore still requires manual test. Signed releases require keystore; do not publish unsigned APK as signed.
- Timeout behavior: Cannot detect exact ChatGPT timeout moment; use this file and Git/CI timestamps for last durable checkpoint.
