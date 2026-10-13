# Monogatari v0.12.0 — completed development batch

**Last confirmed:** October 12, 2026, 8:13:54 PM CDT. This is an observed timestamp, not a detected model-timeout instant.

## VERIFIED DONE
- Tunnel to Surface was the first tool used for local and GitHub access. All repository operations went exclusively through it.
- First-run source setup, selection rules, explicit opt-in and visible Explore progress:
  commit `3756a4ca1`.
- Per-source update-on-error and known-host deduplication/search fallback:
  commit `61929625d`.
- v0.12.0 (versionCode 4) and three guided Android QA tests:
  commit `1f91e9021`.
- Exact cumulative branch `batch-37b-38a-repair` at
  `1f91e90210553d6411ca0f1a0123084dcf60aa07`.
- GitHub Actions Build check `38329819526` **SUCCESS** (13m59s): JVM unit tests,
  debug APK assembly and artifact upload, unsigned R8 optimized build and artifact upload.
- Fast-forwarded `devel` to `1f91e90210553d6411ca0f1a0123084dcf60aa07`. Remote SHA checked.
- Published public GitHub Release `v0.12.0-source-setup-preview`, verified Latest:
  https://github.com/coolchan03/Monogatari/releases/tag/v0.12.0-source-setup-preview
- Debug-signed APK available:
  https://github.com/coolchan03/Monogatari/releases/download/v0.12.0-source-setup-preview/Monogatari-v0.12.0-debug.apk
- APK size 46,972,798 bytes, SHA-256
  `d45bf11134c1c8a82ee3f972b4b13e4a572dc7302646ec9b2326e7207a0eada0`.
  `SHA256SUMS.txt` is also attached and release targetCommitish matches exact tested commit.
- Untracked user folders `.release-temp/` and `source-audit/` preserved.

## PARTIAL — NOT MARKED FULLY COMPLETE
- Step 36: first-run consenting install of English/all-language runnable JS sources, concurrency 4,
  dedupe by known parser-name/site-host heuristic, plus manual retry and progress.
  Real device testing is outstanding. Unknown parser domains are not reliably deduplicated.
- Step 37: installed-only JS repair checks after 3 qualifying failures, existing staged rollback
  protections. Automatic re-testing/once-retry of repaired site and 30-day repo removal policy
  remain unfinished.
- Step 38: groups same known host and media type for Explore and global search, falls back to
  a second known implementation when first is empty/fails. Unknown parser hosts stay separate;
  auto-migrating dead-library titles with read progress preservation and Undo is not done.
- Step 40 nightly external extension-repo health Action not done.
- Step 6 production signing still blocked on keystore and signing secrets.
- Real Android smoke tests (initial setup, content access, updates, background audio,
  backup and restore) not performed by CI. User should run Settings > Monogatari Test Center
  and copy full report.

## Master-plan count
The guide contains 75 numbered steps. Development has reached Step 39, so **36 listed steps
(40–75) remain beyond it**, in addition to unfinished 36–38 and earlier acceptance gates.
Do NOT claim 39 fully accepted stages; source verification and device testing remain.

## NEXT BATCH
1. Tunnel to Surface first for all repository/GitHub work.
2. Check real-device Test Center report, fix failures if provided.
3. Finish Step 37 targeted repair verification and delayed removals and Step 38 actual host matching.
4. Tackle Step 40 extension-repo nightly health checks if supported; do not label unsupported
   live extractors healthy without testing.
5. Stage H starts Step 41 newer-wins restore; user data safety and Undo take priority.
6. Continue 20–30-minute grouped edits, commit recovery notes and test before publishing.
7. Do not claim ongoing coding after the answer; GitHub Actions can run separately.
