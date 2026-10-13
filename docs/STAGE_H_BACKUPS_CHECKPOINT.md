# Monogatari Stage H backup checkpoint — October 12, 2026

Observed at 8:58 PM America/Chicago. This is a checkpoint, not proof of any completed CI run or a detected timeout.

## Step accounting
- Current master guide explicitly defines Steps 1–74, not 75; Step 75 has no description.
- After the last previously implemented Step 39 there are 35 numbered steps (40–74), but past work and Stage G steps still require acceptance checks. Do not claim a percentage of fully completed functionality based on numbered headings.
- Stage G Step 37 / Step 40 work is being done independently in other worktrees and CI; avoid overwriting those branches.

## Starting release and workflow
- Last **verified published** release: v0.12.0-source-setup-preview.
- GitHub: https://github.com/coolchan03/Monogatari/releases/tag/v0.12.0-source-setup-preview
- Default devel baseline for this isolated Stage H worktree: 1f91e9021.
- All GitHub edits and Windows operations via Tunnel to Surface ONLY; no local Android SDK/JDK.
- Preserve untracked .release-temp/, source-audit/, and user files.

## Implemented/pushed in isolated Stage H worktree
Windows worktree: C:\Users\coolc\AppData\Local\Temp\monogatari-stageh-restore
- Step 41 commit 807347e09, branch batch-41a-newest-wins: deterministic newest-wins merge of history/favourites/categories with deletion/tombstone precedence; include tombstones in exported archives; bookmarks and statistics union with insert-ignore; stable per-device backup identifier and name, excluded from Android's own backup. JUnit regression tests for older/newer/tie/delete and backward-compatible archive fields. CI #38334004451 SUCCESS at 9:02 PM CDT (unit tests, debug APK, unsigned R8). Not merged to devel separately; later Step 42 candidate includes it.
- Step 42a commit 38187cb53, branch batch-42a-catalog-backup: new REPOS and OPDS_CATALOGS archive sections, safe HTTPS repository URLs, sanitized OPDS URLs and dangerous/sensitive settings keys, merge restored catalogs. CI #38334131287 FAILED on missing exhaustive restore-UI branch; not merged.
- Step 42b commit 9de4bc48c, branch batch-42b-source-settings-backup: SOURCE_SETTINGS section for allowlisted per-parser preferences; excludes credentials, cookies, CF user-agent, and unknown future keys. CI #38334224342 FAILED on same UI enum omission; not merged.
- Step 42c commit 93adbffd0, branch batch-42c-restore-section-ui: added UI section labels/when branches for REPOS/OPDS_CATALOGS/SOURCE_SETTINGS. CI #38334477332 in progress.
- Step 42d commit 8be646378, branch batch-42d-preserve-local-secrets: safe SETTINGS restore merges ordinary preferences instead of wiping local password/token settings. CI #38334546549 in progress.
- Step 42e commit 582c45b2f, branch batch-42e-encoded-token-filter: rejects URL parameters with encoded credential names (including double-encoded names); CI #38334785291 in progress. This is the newest cumulative candidate branch.

## NEXT ACTION (Tunnel first)
1. Inspect latest cumulative CI run 38334785291 on commit 582c45b2f (and parent 38334546549). If red, use gh run view --log-failed, repair, push another isolated commit/branch and run full CI.
2. Never fast-forward devel or publish a release without full successful exact-commit tests, debug APK and R8.
3. If Stage G devel advances, rebase/cherry-pick this Stage H stack onto latest devel and rerun full CI before merging.
4. Step 42 remains PARTIAL: still needs JS_SOURCES metadata and automatic post-restore reinstallation, and broader source preference coverage with clear secret exclusions. Check any previous agent changes before editing.
5. Step 41 archive restore has not been tested on real Android, so do not call device acceptance complete. Android backup/restore is user-dependent.
6. Step 43 onward folder sync / WebDAV / foreign backup imports remain not implemented.
7. Continue substantial grouped work in separate worktrees to avoid conflicts with scheduled hourly runs. Do not touch user data and never hard-reset.

## Testing and user handoff
- JUnit tests are created for the merge policy, old index/category compatibility, safe URL handling and safe parser preferences.
- For Android: restore an older backup after reading newer pages; restore a backup containing a deleted category; verify bookmarks and stats remain intact; test two backup archives made on different devices and verify device IDs differ; check that source cookies/tokens and URLs with embedded credentials were not exported.
- IMPORTANT PRIVACY EXCEPTION: existing optional IDENTITY section deliberately backs up a community identity secret (for comments/ratings continuity). Do not claim the entire archive contains no secrets. Treat user backup ZIP files as sensitive until future optional encryption exists.
- Android BackupAgent still restores the existing community identity section by design. New per-source and repo sections exclude auth information by explicit policy; future JS source metadata restore should not export executable source code or credentials.
