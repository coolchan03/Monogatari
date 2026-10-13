# Finalization Part 1/5 — repair & stabilization, pass 1 (2026-10-13)

## Verified baseline
- Branch `batch-overnight-stageg-stageh`, prior HEAD `f89a63cfc91f11972ea63a87bcd1638fbb30b300`.
- Exact-commit CI run `38404894131` remained in JVM unit tests on last poll; debug APK/R8 not yet verified. Do not publish an APK until a green exact-commit run and device testing.
- Preserve the Windows integration worktree and main repository, no resets or cleans.

## Repairs in this pass
- Local text/HTML reader's TextToSpeech now narrates rendered plain text rather than raw HTML tags.
- Stopping narration invalidates the current callback generation, preventing an already-queued `onDone` callback from starting the next paragraph after Stop.
- Closing the reader similarly invalidates speech callbacks before engine shutdown, preventing a speak-after-destroy race.

## Remaining Part 1 issues / acceptance gates
- Room v30→31 migration needs migration tests on a real older database, integrity checks, and device upgrade verification.
- Gradle JVM, debug APK and R8 all need to pass for the latest exact SHA.
- Review generated-cover Coil error/fallback integration, crash-dialog external activity behavior, widget updates, source compatibility, and backup/restore on physical devices.
- No SDK/JDK installation and no application-data deletion.
- Further CI failures must be repaired before Part 2 or a new preview release.
- Crash issue-report action now uses real newline-separated GitHub issue body fields and handles the absence of an installed browser without crashing the crash dialog.
