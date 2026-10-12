# Monogatari live batch status
Last observed: October 12, 2026, approximately 4:00 PM CDT (not an exact timeout timestamp).

## DONE — CI passed and merged into devel
- Step 30 core: saved OPDS source IDs, registry, EPUB acquisitions. CI 38306030991 passed. Default devel fast-forwarded to e7a4e0025; remote verified.
- Earlier Steps 1–29 code milestones: merged after successful cumulative CI. Real-device verification remains outstanding.

## IN PROGRESS — pushed; CI not yet passed
- Step 31 LibriVox: audio catalog and section MP3 streams. Original CI 38306667542 failed due nullable description; repair d7d0af4b6. Next CI 38307411790 failed due nullable publicUrl; repaired in d51c64012.
- Step 32 OPDS nested catalogs: same-origin traversal, depth 2, max 12 feeds / 300 EPUB entries, tests. Commit 78246e66c.
- Latest cumulative branch batch-32-opds-navigation tip d51c64012, CI 38307899863 pending. Do not merge until green.
- Step 31 MediaSessionService and background audio playback still not implemented. MediaReaderActivity pauses onStop.
- OPDS PDF/CBZ acquisitions not supported.

## NEXT EXACT OPERATION
1. Request Tunnel to Surface FIRST. Check CI 38307899863 and devel CI after Step 30 merge.
2. Repair errors on latest cumulative branch and rerun. Only fast-forward passing code.
3. Implement MediaSessionService/background controls, validate foreground-service permissions and Android lifecycle.
4. Report each step as DONE, IN PROGRESS or BLOCKED, including commits and tests.
5. Preserve user folders .release-temp/ and source-audit/. No claim of exact timeout instant, signed APK, or device success without evidence.
