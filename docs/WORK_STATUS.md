# Monogatari live work checkpoint

**Last confirmed local time:** 2026-10-12 15:11:59 CDT, America/Chicago. This is the last observed operation, NOT a detected timeout. ChatGPT cannot report its own unexpected timeout instant.

## Operational rules
- FIRST tool action in every new Monogatari work turn: request Tunnel to Surface access; explicitly ask the user if approval is required.
- All repository edits, commits, merges, releases, GitHub Actions inspections, and pushes must run through Tunnel to Surface. Never use the GitHub connector for writes.
- Group compatible guide steps into ~20-30-minute work batches, with staged recovery commits and precise checkpoints.
- Do not claim a build, release, device test, or merge was successful until independently verified.

## Repository state
- Local root: C:\Users\coolc\Codex Projects\Manga-Reader
- Current work branch: batch-25-internet-archive
- Tip: a00b2786b (GitHub push verified)
- Last verified default devel: 3814b2c02 (Steps 1-20 implementation, except signed release and device checks)
- Existing untracked user folders .release-temp/ and source-audit/ were preserved, must not delete or include them in commits.

## Verified milestones
- Steps 1-5: completed earlier, CI verified.
- Steps 8-16: cumulative CI run 38297511275 success, merged at 68cceeac1.
- Steps 17-18: cumulative CI run 38298920743 success, merged at da2e12b5b.
- Step 19: continuous media interface and reader routing, commit ca9d63cbe, CI 38300397563 success.
- Step 20 base AUDIO type, search/Explore/History/Favorites filtering, player dispatch: commit 3814b2c02, CI 38300577106 SUCCESS (debug and unsigned R8 release). Merged into devel at 3814b2c02 via tunnel.
- Devel CI run 38300298490 passed for prior default. New devel CI 38302106275 in progress after Step 20 merge.

## New source/reader branch (NOT yet merged)
- Step 20 cover artwork: 2ab3320e7 on ancestry of batch-25; CI 38301683750 in progress at last check.
- Step 21 built-in source registration, Gutenberg stub: db3647aaa; Step 22 per-host polite network interceptor: e76265c9d. Their original CI failed because enum attempted to override final Enum.name; fixed in Step 24 cumulative commit.
- Step 23 shared EPUB engine EpubBook extracted from JsEpubBridge, used by LocalEpubReaderActivity, regression test: f3bf880f7. Its old standalone run failed the same inherited enum bug, NOT an EPUB-specific test failure.
- Step 24 live Gutenberg Gutendex listing, EPUB downloads/cache, chapter mapping/reader, JSON mapping tests: 58ca805ce. CI run 38301501700 SUCCESS including unit tests, debug APK and unsigned R8 APK. This commit also fixes the BuiltinSource sealed source ID.
- Step 25 Internet Archive books/video/audio/CBZ adapters: 3d2a6a7b7; native stream routing and query regex fix: c0800be53; compatible User-Agent headers and English-only preset inclusion: a00b2786b. Cumulative branch batch-25-internet-archive.
- Step 25 CI run 38302529735 IN PROGRESS as of last observation. Previous run 38302131932 was canceled due new push after unit tests; not a code failure.
- Live remote smoke checks via tunnel: Gutendex default Python User-Agent gave HTTP 403, Mozilla-compatible header returned six Pride and Prejudice results. Archive advancedsearch with same header returned records; one audio item's metadata listed five files, one MP3. Header fix committed in a00b2786b.

## Important remaining work
1. FIRST check run 38302529735. If failure, retrieve log and repair cumulative latest branch; don't merge until CI green.
2. Check Step 20 artwork run 38301683750 and devel run 38302106275.
3. Once latest cumulative source branch CI passes, fast-forward tested tip into devel, verify ls-remote and default branch CI. Avoid publishing an unverified signed release.
4. On-device testing remains necessary: install with backup/restore guidance, open JS novel/video, EPUB, Gutenberg, Archive MP3/MP4/CBZ; verify source index and media artwork.
5. Step 25 PDF fallback and archive CBZ extraction runtime validation are not complete; only EPUB books supported for now.
6. Step 6 signed release requires signing keystore/secrets, not yet provided. Step 7 external developer verification pending. Other original steps 26-74 not yet implemented.
7. Step 10 GitHub Action for extension-repository hash generation still outstanding.

**Next exact command through tunnel:** gh run view 38302529735 --repo coolchan03/Monogatari; check CI failures, then decide whether to merge or fix.
