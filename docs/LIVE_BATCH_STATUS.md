# Monogatari live development and release checkpoint

**Last verified:** October 12, 2026, 6:42 PM CDT (America/Chicago). This is the last observed operation, not an automatically detected timeout.

## DONE — fully verified build, merge, and published testing release
- Final preview branch `batch-32n-video-submenu-fix` at commit `f17f3d855071a8fb23999a97430d211d80a6e396`.
- GitHub Actions `Build check` run `38322296940` **SUCCESS**: unit tests, debug APK, unsigned R8 release APK.
- Fast-forwarded `devel` to `f17f3d855071a8fb23999a97430d211d80a6e396`; remote SHA verified.
- Public GitHub Release `v0.10.0-source-preview` **PUBLISHED and marked Latest**:
  https://github.com/coolchan03/Monogatari/releases/tag/v0.10.0-source-preview
- Verified release APK:
  https://github.com/coolchan03/Monogatari/releases/download/v0.10.0-source-preview/Monogatari-v0.10.0-debug.apk
  - 46,909,752 bytes; archive inspected (1,999 entries).
  - SHA-256 `f8214c1403bd47b13eab0bfa3751dddd6ca72e258ce61079e2385b0731eff541`.
  - Checksum file also attached: `SHA256SUMS.txt`.
- New implementation milestones from this batched session:
  1. `e014419f8` service-specific NewPipe search defaults and unique page cache keys.
  2. `8ed0c391a` visible video / channel / playlist selection in unified video search.
  3. `7becaae3d` bounded continuation pages (up to 8 pages / 240 items) with deduplication.
  4. `714eb5b6e` YouTube RSS recent uploads for tracked channels; live RSS test gave 15 valid unique entries.
  5. `d01b3ae70` versionCode 2, versionName 0.10.0, five new guided QA questions.
  6. `8c9bba8fe` preserve earlier chapters when later network pages fail.
  7. `94c277ac0` consistent search mode prefix selection.
  8. `686ce5e1c` rank video search results by title query rather than internal prefix.
  9. `f17f3d855` hide video-only menu options outside the video submenu.
- Earlier related CI builds all passed; full final cumulative run is the release gate and passed.
- PeerTube and SoundCloud integrated previously, verified CI, now included in this test version.
- User folders `.release-temp/` and `source-audit/` preserved untouched and untracked.

## IN PROGRESS — feature acceptance, NOT falsely counted as finished
- Real Android device tests: YouTube/PeerTube video picture and sound, SoundCloud and LibriVox background audio/notification/position, manga/novel chapter reading, source installs, backup/restore, and UI filters.
- Test Center is available; user should mark pass/fail and copy full report after installing the test APK.
- More Stage G features (Steps 35–40 source health, self-repair, updates, website deduplication, presets) remain to be built.
- Actual Wattpad endpoints in Step 33 need verified/public APIs; don't invent scrape endpoints.

## BLOCKED / user-dependent
- Production-signed APK requires signing credentials. GitHub secret listing showed ONLY `MONOGATARI_REPOS`, no `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEY_ALIAS`, `RELEASE_KEYSTORE_PASSWORD`, or `RELEASE_KEY_PASSWORD`.
- Step 7 external developer verification requires user action.
- Debug APK signature may differ from a prior testing APK. Back up first, and do not promise in-place installation will work.

## Step accounting
- 75 numbered steps in master guide; code implementation has reached Step 32 with some incomplete acceptance gates (not 32 fully accepted). Steps 6 and 7 are explicitly outstanding. Many later steps 33–75 not completed.
- Do not claim complete Kotatsu/Mangayomi parity, actual on-device success, or production signing based only on CI.

## NEXT BATCH — Tunnel to Surface must be FIRST
1. Confirm final GitHub Release assets and SHA, branch SHA, latest status if requested.
2. Audit Test Center report from Android user; repair real crashes/site issues.
3. Plan Stage G steps 35–40 (source health, automatic repair, rollback) in 20–30 minute batches.
4. Preserve user data and untracked folders; GitHub operations exclusively through Tunnel.
5. End with DONE / IN PROGRESS / BLOCKED and exact commits/CI/release links.
