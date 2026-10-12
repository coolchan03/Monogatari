# Monogatari live work checkpoint

- Last checked: 2026-10-12 13:24:51 CDT (America/Chicago; exact time of latest local environment check, NOT a detected timeout)
- Active work batch: Stage A/B, original Steps 3-5 of 74
- Current operation: Commit progress/QA checkpoint, then push branch batch-04-05-identity-branding and inspect both GitHub Actions build runs
- Access: Tunnel to Surface verified. ALL repository edits, Git operations, CI inspections and releases through tunnel only
- Local repository: C:\Users\coolc\Codex Projects\Manga-Reader
- Working branch: batch-04-05-identity-branding (based on d49d1134f)
- Step 3: CI release APK and QuickJS/R8 keep rules at commit d49d1134f, pushed as batch-03-05-safety-identity. GitHub Build check run 38289292970 currently in progress; debug-apk artifact exists; release R8 outcome pending
- Step 4: Android ID and version updated to io.github.coolchan03.monogatari, versionCode 1, versionName 0.9.0. Sync authorities fixed for main/debug/nightly. Commit 8c6382096. Code complete, device backup/restore and build verification pending
- Step 5: Replaced inherited app-brand strings in 35 files (188 line changes), including localized user-facing strings. Preserved old Kotatsu backup format, the actual Kotatsu Backup Bot and upstream sponsorship references. Kept all 31 functional kotatsu://about deep links. Commit e786847b1. Code complete, build verification pending
- QA completed: git diff --check passed; Python static check passed (all 60 strings.xml files parsed as XML, 3 authority variants, app ID/version/label and internal link checks). No Android SDK platforms on local machine, so native Android build relies on GitHub CI
- Remote status: devel is on original Step 2 commit 29ed10076; branch batch-03-05-safety-identity pushed at d49d1134f; new branch not pushed as of this checkpoint
- Untracked local user work deliberately left alone: .release-temp/ and source-audit/
- Next exact operation: Commit this checkpoint and docs/PROGRESS.md, push batch-04-05-identity-branding, inspect the second CI run and the first R8 build result
- Device-dependent gate: User must back up existing app through Settings -> Backup before testing newly renamed app and restore the backup into the new package. Do not replace installed app automatically
- Publication gate: Step 6 signed releases require actual signing secrets; no signed APK or production release created yet

## Session rules
1. At the start of each new Monogatari work session, request Tunnel to Surface FIRST, even before GitHub read or other operations. Ask for user authorization if unavailable.
2. Do not use GitHub connector to write, merge, publish or change branches; all repository operations occur via Tunnel to Surface.
3. Combine adjacent compatible original guide steps into substantial batches, tracking individual steps, test outcomes and recovery commits.
4. Before risky/long-running operations, record what file/command is active here and tell the user. After verified milestones, record commit and CI outcome.
5. Unexpected model timeouts cannot be detected or reported at the exact moment. This file's timestamp is the last durable checkpoint; reconstruct the actual stop from Git timestamps and tool logs.
6. Never claim a successful build, push, live device test or release without verifying it. Preserve existing untracked work and user data.
