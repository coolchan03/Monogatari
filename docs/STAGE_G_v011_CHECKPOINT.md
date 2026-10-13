# Monogatari Stage G batch recovery checkpoint — October 12, 2026

Last confirmed operation 19:09 CDT (America/Chicago). This is an observation, not an exact timeout detector.

## Existing proven starting point
- Public Latest debug APK v0.10.0-source-preview is already published.
- Default devel was f17f3d855 at batch start; baseline CI #38323474880 passed.
- User requires all local/GitHub edits through Tunnel to Surface FIRST, and substantial 20–30 minute grouped coding batches.
- Preserve untracked local folders `.release-temp/` and `source-audit/`.

## Implemented & pushed (NOT merged until cumulative CI success)
- Step 35a commit `66620f38b`: database schema 29 -> 30, Migration29To30, fail_count + last_success_at, per-source success/failure recorder, unhealthy auto-global-search suppression, Explore warning and lowest priority, manual per-source retry still possible. CI #38324757604.
- Step 39 commit `95be6d5eb`: one-time editable default presets: BL & Yaoi, Popular manga, Books & fanfic, Anime & video, Audio. No replacement or recreation of custom/deleted presets. CI intermediate runs canceled by later commits on same branch.
- Step 37 file safety commit `74c16437b`: staged .new files, previous .prev files, crash-recovery marker, restore previous, 48-hour automatic-update pause. JVM rollback/recovery tests added.
- Step 37 scheduling / rollback commit `4d92e1a89`: WorkManager installed-JS updates every 12 h, app-start check if 6 h have elapsed, 4 concurrent installs, automatic rollback on 3 failures only if previous install healthy and updated recently. Builtin and JS source health rows, digest/version validation still performed.
- Version 0.11 test release preparation commit `ea5cc7b8d`: versionCode=3, versionName=0.11.0, source update status + manual check in Sources, 3 new Test Center checks, CHANGELOG, docs/RELEASE_0_11_0_NOTES.md.
- Source domain/no-network refinement commit `2d0d25edf`: use existing ProbeFailureFilter to distinguish dead site/DNS vs offline/VPN, using recent other-source successes.
- Cumulative branch `batch-39-default-source-presets` latest tip `efee8f3f9`. First cumulative CI #38325809860 failed because WorkManager Scheduler's Android Context was missing its Hilt @ApplicationContext qualifier. Fixed by commit `efee8f3f9`. Latest CI #38326334853 running; do not publish or merge until SUCCESS. The earlier Step 35 baseline CI #38324757604 passed full tests/debug/R8.

## Release tasks only after full CI passed
1. Use Tunnel to Surface first. Check CI #38326334853 on commit efee8f3f9. If red, inspect --log-failed, patch and run new CI; do not force merge.
2. Fast-forward devel from `origin/batch-39-default-source-presets` ONLY AFTER full CI green; verify remote SHA.
3. Publish public **debug-signed** APK to tag `v0.11.0-source-health-preview` with SHA256SUMS.txt and release notes. Prepared tunnel-local script `C:\Users\coolc\AppData\Local\Temp\monogatari_publish_v011.py` accepts `--run <passed_run_id> --expected-sha <commit_prefix>`. It rejects non-success CI and mismatched SHA. Check the published release and assets before giving links.
4. If time allows, use Settings -> Monogatari Test Center on Android, copy report and repair issues in a later batch.
5. Update this checkpoint on notes branch after release.

## Deliberate exclusions (do not claim complete)
- Production APK signing still blocked by absent GitHub keystore secrets (only MONOGATARI_REPOS configured).
- Database migration instrumentation/device tests, source playback/site compatibility, CAPTCHA, actual rollback on a device still pending. CI does not prove these.
- Step 36 mass auto-install of all newly indexed third-party sites not implemented; installed-only sources update automatically to preserve user control and data.
- Step 37 source removals after 30 days and on-failure immediate network retry are not implemented.
- Step 38 one-per-host failover/auto-title migration, Step 40 external repo nightly health check, and later stages remain outstanding.
- 75-step guide has reached Step 39 code milestones, not 39 fully verified/accepted steps; avoid claiming 39/75 complete.

## Saved state
- All coding GitHub commits use Tunnel; no local Android SDK or JDK installed.
- Untracked user folders must remain untouched.
