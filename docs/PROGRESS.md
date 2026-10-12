# Monogatari progress (current checkpoint)

Updated October 12, 2026. This is a code/CI milestone log, not proof that all device acceptance criteria are complete.

- Steps 1–5: application baseline, CI builds, app identity and branding code integrated. Backup/restore on real devices still needs verification.
- Step 6: production-signed release blocked on keystore and GitHub Secrets. Public test builds are debug signed, not production signed.
- Step 7: external developer verification is user-dependent and incomplete.
- Steps 8–18: security/privacy and search improvement code previously implemented, merged, and covered by cumulative build checks; some step-specific tests and device verification remain outstanding.
- Steps 19–32: shared book/audio/video readers and built-in sources implemented in stages and compiled. Last major source batch (NewPipe YouTube, PeerTube, SoundCloud, LibriVox, OPDS) has passing CI, but actual device/media/site acceptance remains necessary.
- Step 32, source reliability and preview UI sub-batches f–n: **DONE for implementation and CI**, final run `38322296940` SUCCESS, merged `devel` at `f17f3d855`.
- Version `0.10.0`, versionCode `2`: public **debug-signed** APK test release published and marked Latest: https://github.com/coolchan03/Monogatari/releases/tag/v0.10.0-source-preview.
- Steps 33–75: not completed. Use `docs/MONOGATARI_MASTER_STEPS.md` for requirements; prioritize user-requested Stage G and actual user feedback.
- Full instructions/recovery: `docs/LIVE_BATCH_STATUS.md` on the checkpoint branch.

Important: 32/75 indicates the numbered stage reached, not 32 fully accepted steps. Never substitute compilation for tested Android playback, and never publish a production-signed release without the signing credentials.
