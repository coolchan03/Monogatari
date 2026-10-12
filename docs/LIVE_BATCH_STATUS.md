# Monogatari live work checkpoint
Last observed: 2026-10-12 16:25:15 CDT (last observation, NOT a detected timeout).

## DONE — CI verified and merged
- Saved OPDS first-class sources and bounded OPDS navigation: cumulative CI 38307899863 success; merged to devel as d51c64012.
- LibriVox basic source plus Media3 background playback, Android foreground media service, lock-screen media session, audiobook artwork and local hashed-URL resume positions: cumulative CI 38309024466 SUCCESS, with debug APK and unsigned R8. Merged to default devel at e8344e8a4 (remote verified). Real Android device smoke testing not yet done.
- Earlier Steps 1–30 code milestones have passed CI and are merged, subject to device testing.

## IN PROGRESS — pushed but still CI testing
- Step 32a: official NewPipeExtractor v0.26.5, OkHttp downloader, initialization, ProGuard rules: commit a994f3e13, branch batch-32a-newpipe-dependency, CI 38309305378 R8 in progress at last observation. Do NOT merge without green.
- Step 32b/c partial: YouTube VIDEO search/pagination, stream resolution with HLS, DASH, merged video-only/audio, and subtitles; guided test cases for LibriVox/OPDS/YouTube. Current branch batch-32b-newpipe-youtube at 40a2d1ace, CI 38310462619 IN PROGRESS. Initial CI 38310057317 FAILED because NewPipe URL fields are nullable; fixed in 40a2d1ace. Do NOT merge until latest cumulative build passes.
- Devel post-audio-merge CI 38310322371 IN PROGRESS.
- YouTube channels/playlists, PeerTube and SoundCloud, RSS and real playback tests remain incomplete.
- Signed release still needs keystore/secrets, no signed release claimed.

## NEXT EXACT ACTION
1. Tunnel to Surface access FIRST, and ALL GitHub changes via tunnel.
2. Inspect CI runs 38309305378, 38310462619 and 38310322371; diagnose errors via gh run view <id> --log-failed.
3. If NewPipe dependency passes, the latest cumulative YouTube branch still needs to pass before merging it into devel. Check merge ancestry and ls-remote after fast-forward.
4. Test on device: LibriVox screen-off playback, notification/lock-screen, resume; OPDS nested feeds; YouTube video with picture+sound, HLS/DASH and external subtitles. Use Advanced > Test Center and Copy report.
5. Preserve local user folders .release-temp/ and source-audit/.
6. End each batch with explicit DONE / IN PROGRESS / BLOCKED and latest commit+run references. A pushed commit alone does not count as DONE. Cannot detect exact timeout instant.

The user prefers 20–30-minute substantive batches and explicit completion updates.
