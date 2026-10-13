# Monogatari recovery checkpoint — reader metadata, backups, navigation, artwork

Date: 2026-10-13, CDT. This branch was recovered from uncommitted source files left in the overnight integration worktree. A binary patch and three new files were copied to an isolated worktree. Original working files and all user folders were left untouched.

## Source baseline
- Prior stable development branch: `origin/batch-overnight-stageg-stageh`, commit `ecc15c260`; full GitHub Actions CI `38425738232` SUCCESS. Still not merged to devel at recovery start.
- Public pre-release `v0.13.0-stagegh-preview` is a different, earlier commit `d01583fd1` and does not include this recovered work.
- Recovery branch: `batch-recovery-stats-navigation-20261013`.

## Recovered code, pending cumulative CI
- Step 46–48: reader-observed manga pages, book word count, audio/video duration, chapter_meta read/write and weighted reading-time calculation; avoid counting incognito sessions.
- Statistics backup models preserve old archive compatibility and add optional chapter, words, media duration fields; chapter dimensions get a distinct optional backup section with bounded merge policy.
- Step 49: optional More bottom-navigation destination using the already-existing MoreActivity. Existing default Books tab remains in the untouched default ordering; existing saved navigation values remain unchanged.
- Step 51: offline generated covers for app shortcuts and chapter notifications when remote artwork is blank/unavailable.
- Fixed duplicate GeneratedCoverDrawable import and wrong two-argument constructor in two call sites; added missing chapter_meta DAO dump method.
- Added three JVM tests for old statistics backup defaults, expanded statistics backups, and chapter metadata backup roundtrips.
- XML vector, values and ID files parsed successfully; git diff --check clean.

## Acceptance gates
- GitHub Actions JVM, debug APK, unsigned R8 release must all pass at exact branch SHA. This branch must NOT be merged or released while CI is red.
- Room v30→31 migration and v31 backup/restore still require Android device tests, including upgrading an older on-device database with library and reading history intact.
- Reader estimates are approximate where chapter page/word/duration data are unknown; they are NOT a guarantee or a tested time-to-finish UI.
- Real phone/tablet checks required for more-tab navigation, reader progress and generated covers.
- Production signing/secrets and developer verification remain user-dependent.

## Next steps
1. Inspect exact-branch CI; fix compiler or test failures and rerun until green.
2. Merge only a verified branch after safety review. Publish a clearly debug-signed test prerelease rather than claim a production release.
3. Continue Part 2/3 gaps: real foreign importer preview+transactional merge/undo, private-category enforcement, offline translation runtime, secure two-device WebDAV sync, source compatibility, host diagnostics, and Android migration tests.
4. Stage 74 final release checklist is NOT complete. Master guide defines 74 numbered steps, and 69 are touched/partial; only Steps 1–2 were fully recorded as accepted in the latest audit.
