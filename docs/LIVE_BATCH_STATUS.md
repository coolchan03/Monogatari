# Monogatari live work status

Last observed: 2026-10-12 16:22:24 CDT (this is NOT a model timeout timestamp).

## Verified DONE
- Step 30 saved OPDS sources: CI 38306030991 passed; merged earlier.
- Step 31 LibriVox basic catalog + Step 32-style OPDS navigation: combined CI 38307899863 SUCCESS (unit tests, debug APK and unsigned R8 APK). Default devel fast-forwarded to d51c64012 via tunnel; remote confirmed.
- Devel previous CI 38307931064 SUCCESS. Latest devel post-merge run 38309624801 IN PROGRESS.
- Preserved untracked .release-temp/ and source-audit/.

## Pushed; awaiting full CI / device verification
- LibriVox Media3 background playback service, lock-screen notification/session controllers, audio artwork, audio position persistence. Branch batch-33-background-audio tip e8344e8a4. CI 38309024466 IN PROGRESS; unit tests/debug APK passed, R8 still running. Commits 40c294ca5, c6e99b889, cbfec0e9a, e8344e8a4. LibriVox real API smoke showed 128 sections for a test book, working https MP3 URLs, author and genre fields. Device playback NOT tested.
- NewPipeExtractor v0.26.5 and okhttp downloader (Step 32a): commit a994f3e13, CI 38309305378 IN PROGRESS; unit tests/debug APK passed, R8 running.
- First YouTube built-in VIDEO source search, pagination and basic streams: commit 6b869e02b, test center guidance ecd358997, DASH + separate audio/video merging 3dbfea20e. Branch batch-32b-newpipe-youtube at 3dbfea20e, latest CI 38310057317 IN PROGRESS. Not merged. PeerTube, SoundCloud, channels/playlists, YouTube device playback remain incomplete.
- Guided QA report has new LibriVox, OPDS and YouTube cases, but no device user results yet.

## Next exact task (TUNNEL FIRST, GitHub writes only through tunnel)
1. Inspect CI 38309024466 (audio), 38309305378 (NewPipe dependency), 38310057317 (YouTube), and devel 38309624801.
2. If failures: get `gh run view <id> --log-failed` and repair on current cumulative branch batch-32b-newpipe-youtube; push and rerun until green.
3. Merge only passing commits into devel in ancestry order. If audio passes, fast-forward e8344e8a4; if NewPipe and YouTube pass, fast-forward 3dbfea20e. Check ls-remote.
4. Device smoke test remains required; do not claim functionality on hardware without installing/debugging it.
5. Remaining Step 32 work: channels/playlists, PeerTube, SoundCloud, RSS, and DRM/age/failing host handling.
6. Update this LIVE_BATCH_STATUS.md plus user-visible DONE/IN PROGRESS/BLOCKED report.

Timing: user asks ~20–30-minute batches and visible completion. Record operation and exact last observed time; an unexpected timeout cannot be detected exactly.
