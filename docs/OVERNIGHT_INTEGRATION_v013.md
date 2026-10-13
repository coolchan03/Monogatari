# Monogatari v0.13.0 integration checkpoint

## Contents
- Step 37: delay hiding extensions removed from a remote index for 30 days, rather than dropping them immediately; includes retirement policy tests.
- Step 38: compare normalized supported website hosts for Kotatsu parsers and JS alternatives and group matching entries in Explore/global search; includes host/fallback tests. Unknown hosts are not force-merged.
- Step 40: respect recent verified `broken` markers from extension indexes; stale, unknown or unverified markers are not enough to suppress a source; includes status tests.
- Step 41: merged backup restore preserves newer history, favourites and categories and tombstone precedence; bookmarks/stats union; device identity metadata.
- Step 42 (partial): optional repository URLs, OPDS catalog URLs and allowlisted parser settings export/restore; preserve local secrets. JS source install metadata and post-restore reinstall remain missing.

## CI and release safety
- Starting published base: v0.12.0, devel `1f91e90210553d6411ca0f1a0123084dcf60aa07`, CI `38329819526` SUCCESS.
- Cumulative Stage H starting commit: `582c45b2feb297a602d9969e7378daaec09f1db8`, Build check `38334785291` SUCCESS.
- Step 37 individual CI: `38333203894` SUCCESS.
- Step 40 app CI: `38332820639` SUCCESS; extension repository's nightly workflow run `38332852658` SUCCESS.
- Stage G/H combination is new: **must have its own exact-commit CI success** (unit tests, debug APK, unsigned R8) before moving devel or publishing v0.13.0.
- Previous Stage H candidates 42a/42b had compilation failures that were fixed in 42c; only rely on cumulative passing head.

## Incomplete and required manual checks
- Step 37: re-test changed repaired sources and retry original call once; 48-hour rollback/30-day retention needs real-device background verification.
- Step 38: automatic migration of dead library titles and safe Undo are **not implemented**. Preserve reading history and do not move titles silently.
- Step 40: extension extraction checks are best-effort; a nightly PASS does not prove every source works or media playback succeeds.
- Step 41: backup newest-wins JUnit tests pass on candidate, but two-device restore is not phone/tablet verified.
- Step 42: JS source metadata installer, source preference coverage and backup encryption still incomplete; archives may intentionally include user identity secrets. Treat backups as private.
- Steps 43-45 (folder sync, WebDAV, foreign imports) not yet implemented.
- Step 6 production signing remains blocked by missing keystore/secrets. APKs currently debug signed; not device tested.
- Numbering: master guide explicitly defines **Steps 1-74** (no Step 75). Do not equate last reached step with full acceptance.
- Leave `.release-temp/`, `source-audit/`, device data, app databases and user libraries unmodified.

## Next batched implementation
1. If CI is red, inspect exact failing task/log, fix only the integration regression, test a new exact commit.
2. On green build, publish debug APK preview; never assert this is production-signed.
3. Continue Step 42 missing JS source restore and Step 43 opt-in folder sync with per-device cursors and merge-safety tests.
4. Record unverified device cases for the morning repair pass.

## 9 PM overnight batch result (2026-10-12, CDT)
- Local worktree: `C:\Users\coolc\AppData\Local\Temp\monogatari-overnight-integration`.
- Branch: `batch-overnight-stageg-stageh`; integrated cumulative Stage H 42e with source retirement, website grouping and verified index-health commits.
- Cherry-picked: `a8a76d245` -> `ff542de16`, `669110fa9` -> `14d6fa315` (manual constructor merge), `2b23f3382` -> `aa7776682`, `1f6c198c1` -> `9ae0f3d7c`.
- Local release preparation commit `706fc5067a9713ab1af1e177cfa6659594ce125e`; `versionCode=5`, `versionName=0.13.0`.
- Local Git diff whitespace check passed. Separate source-retirement, index-health and Stage H branches have green CI, but **the combined branch has not passed CI**.
- GitHub push from Tunnel was denied by OpenAI's safety checks (two attempts); no new remote branch, CI run or release asset exists for this commit. Do **not** merge/publish until exact-commit CI runs green and permission allows push.
- The laptop cannot run this Gradle build locally without further setup: `java` absent from PATH, Android 37 platform and `local.properties` absent. Do not install an SDK/JDK without consent.
- User files and untracked `.release-temp/` and `source-audit/` were untouched.

## 10:55 PM grouped Step 43 hardening checkpoint (2026-10-12 CDT)
- Added opt-in six-hour WorkManager folder sync and on-launch catch-up, with per-device merge cursors and deterministic newest-snapshot selection.
- Added pure planner regression tests and a restore safety test: a backup must not activate folder synchronization.
- Shared-folder sync now writes only INDEX, HISTORY, CATEGORIES, FAVOURITES, BOOKMARKS, STATS, excluding identity, source credentials, and settings.
- Sync snapshots use `sync_` filenames; retention deletes only this device's `sync_` snapshots, not normal full backups or other devices' files.
- When folder sync is enabled, the pre-existing periodic full-backup service is disabled to prevent secret-bearing backups and global retention from affecting other devices. Users can still create manual full backups.
- App ID checks allow only explicit Monogatari release/debug/nightly package identifiers.
- Do not claim verified until combined-commit CI and device smoke testing pass. Actual phone/tablet two-device merge and SAF permissions remain untested.
- Step 42 JS-source metadata/reinstallation, Step 44 WebDAV, Step 45 migrations, production signing and earlier Stage G partial features remain pending.

## Subsequent Step 42 JS-source restore batch (2026-10-12 CDT)
- Added JS_SOURCES to archive sections, containing per-source ID, repository index URL, and installed version; executable JavaScript code is never embedded in this section.
- On an explicit backup restore, restored repository URLs are loaded first, the matching source is discovered in the configured remote index, its version is checked, and installation uses the existing network download/integrity validation path.
- Existing sources at an equal or newer version from the same repository are not overwritten. Unavailable sources are recorded as per-item restore failures rather than silently replaced by a different repository's source.
- Source-specific settings are restored only after JS source installation, preserving the existing secret-exclusion policy.
- Added a JS metadata serialization round-trip test; note that all new tests are unexecuted locally pending a CI-enabled push.
- User must still test offline restore, sources missing from upstream, protected libraries, and multi-device source compatibility; backup password encryption remains optional and unimplemented.
- Push to GitHub remains blocked by OpenAI safety checks even though a dry-run push reported the destination branch as creatable. Do not bypass the restriction, merge or release without exact-commit CI.

## Verification and authorization state for this iteration
- Step 43 and privacy hardening are committed locally at `8d6589132` (parent `fcbc0b15e`); combined v0.13.0 has still not passed CI.
- Step 42 JS source restoration changes and JUnit metadata test are staged, not committed. A Tunnel `git commit` call was blocked by OpenAI safety checks.
- A Tunnel `git push` call was also denied (not retried or circumvented), despite an earlier successful read-only `--dry-run` branch check.
- `git diff --check` and `git diff --cached --check` succeeded, but JUnit, Android debug APK, R8, device sync and network restore remain untested.
- A read-only XML parser test attempt was also blocked by OpenAI safety checks; do not mark XML validated.
- Do not publish, merge, or claim completed until the specific new cumulative commit passes exact-commit GitHub Actions Build.

## After-midnight Step 42/43 integration hardening (2026-10-13 CDT)
- Confirmed that the Step 42 JS-source metadata/restore batch remains staged separately from the committed Step 43 work. No staged content was overwritten.
- Folder sync now scans only dedicated `sync_*.zip` snapshots, not complete/manual backups that can contain identity and settings. This prevents automatic import of unrelated full backups and lets the bounded scanner prioritize sync snapshots.
- New snapshot filenames incorporate a UUID before the existing date token so repeated exports in one minute do not collide. Legacy `sync_` snapshot filenames remain eligible.
- Added `FolderSyncPlanner.isSnapshotFileName()` and a JVM regression test confirming both old and new snapshot names and rejecting ordinary full backups and non-ZIP notes.
- Changing the folder-sync/periodic switches, or selecting the destination directory, now refreshes WorkManager scheduling without needing an app restart.
- Local repository and staged files were inspected through Tunnel. `git diff --check` and `git diff --cached --check` returned code 0, with only line-ending normalization warnings.
- No Java/JDK/Android SDK installed locally; Android JUnit, assembleDebug, R8 and device checks remain unexecuted. No exact-commit combined CI success or new release exists as of this checkpoint.
- Next: commit the staged JS-source restore + new hardening patch only if Tunnel authorizes it; push via Tunnel only; wait for exact-head GitHub Actions Build success before merging/releasing. Do not bypass any safety denial. Then proceed to Step 44 WebDAV/Step 45 imports as separately tested batches.

## Step 43 shared-folder retention follow-up (2026-10-13 CDT)
- Fixed a second cross-device retention risk: `ExternalBackupStorage.trim()` now excludes dedicated sync snapshots even when automatic full backups are re-enabled later. It never globally trims sync snapshots from another device.
- `getLastBackupDate()` now measures ordinary/full backups only; a newer `sync_` snapshot will not suppress a scheduled full backup or mislabel it as the last completed full backup.
- The Step 42 JS-source restore and preceding Step 43 hardening patch were committed locally at `af76c052cb48dd1af7330836eda06d7a525aba94` after clean staged whitespace checks.
- One normal Tunnel push for `batch-overnight-stageg-stageh` was denied by OpenAI safety checks; not retried or bypassed. Remote branch and exact-commit CI do not exist for this head.
- This retention follow-up is a separate local worktree change requiring staging/commit, GitHub CI and device verification. Android JUnit and R8 are still unavailable on this Windows host without installing build tooling.
