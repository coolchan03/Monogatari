# Monogatari live batch checkpoint — October 12, 2026

Last observed: 18:24 CDT, America/Chicago (NOT an automatically detected timeout).

## DONE — committed and pushed using Tunnel to Surface
- PeerTube + SoundCloud NewPipe native adapters, CI 38314916790 SUCCESS, fast-forward merged to default devel at 58fd81b3c. Post-merge CI 38320483011 SUCCESS.
- 32f source-scoped search modes / pagination keys: e014419f8, CI 38320551815 SUCCESS.
- 32g visual video search mode menu + saved selection: 8ed0c391a, CI 38321019662 pending.
- 32h bounded continuation page fetch/dedup for channels/playlists: 7becaae3d, CI 38321082002 pending.
- 32i official YouTube channel RSS feed parser + tracker refresh: 714eb5b6e, CI 38321338999 pending; public RSS smoke tested and returned 15 entries.
- 32j Android versionCode 2 / versionName 0.10.0, 5 new guided QA cases, release notes: d01b3ae70, CI 38321448613 pending.
- 32k retain chapters if later continuation fails: 8c9bba8fe, CI 38321533655 pending.
- 32l consistent video search mode prefix selection: 94c277ac0, CI 38321664527 pending.
- 32m unified search ranking uses title query not internal mode prefix: 686ce5e1c, CI 38321914456 pending. Branch: batch-32m-mode-search-relevance. This branch includes all the earlier 32f-32m changes in linear ancestry.

## New public testing release requested
- RELEASE TAG TO CREATE ONLY AFTER FULL CI SUCCESS: v0.10.0-source-preview
- Current exact final candidate branch: batch-32m-mode-search-relevance, tip 686ce5e1c.
- Verify CI 38321914456 fully green (unit tests, debug APK, unsigned R8 APK). Repair and rerun if red.
- Fast-forward passing candidate into devel through tunnel; verify ls-remote remote SHA.
- Publish public GitHub Release with debug APK from that EXACT successful CI run, SHA256SUMS and docs/RELEASE_0_10_0_NOTES.md. Do NOT mislabel as production signed.
- Prepared publishing script on Windows tunnel: C:\Users\coolc\AppData\Local\Temp\monogatari_publish_v010.py
- Intended command AFTER CI green and merge: python <script path> --run <green_run_id> --expected-sha <commit prefix>
- Script guards against CI failure, mismatched commit or preexisting release tag; release title prominently says Test APK. GitHub CLI --latest is supported.
- Production signing secrets RELEASE_KEYSTORE_BASE64, RELEASE_KEY_ALIAS, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_PASSWORD are NOT configured (gh secret list showed only MONOGATARI_REPOS), so do NOT dispatch production signing workflow.

## NEXT EXACT OPERATION
1. Tunnel to Surface FIRST for all code/repo/GitHub actions.
2. Check GitHub Actions runs newest 38321914456 plus intermediate f..l. Fix any compiler/R8 errors on latest cumulative candidate branch. Do not merge or release unverified code.
3. After green, fast-forward devel and publish public APK release from exact CI job artifact with checksum, then verify Release URL + asset list and give link.
4. Update this status file on a notes branch (to avoid triggering a new build).
5. Preserve .release-temp/ and source-audit/ as untracked user folders, do not delete.
6. User requests substantial 20–30 minute batches, one explicit DONE/IN PROGRESS/BLOCKED finish; no background coding after final answer.
7. Device smoke tests are still required for player / updates / source compatibility, and debug APK signing may differ from earlier debug releases.

## Beyond this preview
- Full source stability and Stage G health/auto-update steps 35-40 remain unfinished.
- Signed releases Step 6 and external developer verification Step 7 require user credentials/actions.
- No accurate total of fully accepted 75 steps without required device test evidence; code milestones have progressed through 32 with later stages outstanding.
