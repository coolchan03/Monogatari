# Monogatari live work checkpoint

- Last checked: 2026-10-12 13:35:27 CDT (America/Chicago, exact last local environment timestamp, NOT detected timeout)
- Active work batch: Stage A/B, original Steps 3-5 of 74
- Current operation: Commit and push the Step 4 VersionIdTest regression fix, then rerun the full CI build on batch-04-05-identity-branding
- Access: Tunnel to Surface verified. ALL repository edits, commits, CI inspections, merges and releases through tunnel only
- Local repository: C:\Users\coolc\Codex Projects\Manga-Reader
- Working branch: batch-04-05-identity-branding (based on Step 3 commit d49d1134f)
- Step 3: Release R8 rules and CI at d49d1134f. Run 38289292970 PASSED, including debug-apk and monogatari-release-unsigned artifacts
- Step 4: App ID io.github.coolchan03.monogatari, version 0.9.0/code 1, main/debug/nightly sync authorities updated in commit 8c6382096. Phone backup/restore and full build verification pending
- Step 5: 35 localized resource files updated in commit e786847b1; preserved the original actual bot/upstream sponsor names and 31 internal kotatsu://about deep links. Full build pending
- QA static checks PASSED: git diff --check, Android identity and all 3 authority variants, 60 locale XML parses, original functional deep links
- CI regression: run 38290875119 FAILED in Unit tests (111 tests completed, 1 failed, 10 skipped); VersionIdTest.testCurrentVersion asserted current version > old Kotatsu baseline 2.4.6, incompatible with new Monogatari version 0.9.0
- Regression fix: changed test to require >= Monogatari's 0.9.0 baseline and positive versionCode; local git diff --check passed; not yet pushed as of this checkpoint
- Remote status: devel still points to 29ed10076 (Step 2); batch-03-05-safety-identity pushed at d49d1134f; batch-04-05-identity-branding pushed at 7ba3193a8
- Next exact operation: Commit test + docs, push branch, inspect new GitHub Build check until pass/fail, repair further failures before merging into default devel
- Untracked user directories intentionally untouched: .release-temp/ and source-audit/
- Device safety: Before testing app with new ID, back up original app in Settings -> Backup. New package won't automatically share its data; restore backup after installation
- Signing: Step 6 cannot complete until signing secrets are configured; none were reported by gh secret list. Do not publish unverified release

## Session rules
1. At start of EVERY new Monogatari work session request Tunnel to Surface FIRST, even before GitHub reads or other tools; ask authorization if unavailable.
2. Never use GitHub connector for repository changes. All edit/commit/push/merge/release operations happen through Tunnel to Surface.
3. Group adjacent compatible original steps in substantial work batches, preserve individual stage status, commits, failure causes and tests.
4. Before long/risky operations, record exact current operation here and tell the user; after verified milestones, record commit and CI result.
5. An unexpected ChatGPT timeout cannot be detected at its exact moment. This file is the last saved checkpoint; reconstruct actual stop using timestamps, Git commit history and CI logs.
6. Never claim a passing build, device test, push or release until verified; preserve user data and existing untracked files.
