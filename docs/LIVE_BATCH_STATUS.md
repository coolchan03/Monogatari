# Monogatari LIVE batch status
Updated 2026-10-12 15:49 CDT (last observed; NOT a timeout timestamp).

## DONE — passed full CI and merged to devel
- Through Step 25 Internet Archive: run 38302821188 passed.
- Step 26 initial Open Library: run 38303193396 passed.
- Step 26 readable-only setting: implementation in 422125acf; latest run 38304566148 verify separately.
- Step 26 metadata cache (Room 28->29), Step 27 AO3, Step 28 Royal Road, Step 29 Scribble Hub: cumulative branch batch-29-scribblehub at 538c5467a CI 38305370674 SUCCESS, fast-forward merged devel to 538c5467a. The above is code/CI verification only; none of those sources have passed an actual device smoke test.

## IN PROGRESS
- Step 30 OPDS saved catalogs as first-class sources, EPUB-only acquisitions, stable IDs, source registry. Latest commit e7a4e0025, latest run 38306030991 in progress. Other forms including PDF/CBZ and subcatalog navigation still pending.
- Step 31 LibriVox built-in AUDIO source, listing API + sections/MP3 streams, commit c4b8970df, CI 38306667542 in progress. MediaSessionService and background audio controls NOT implemented.
- Devel post-merge CI run 38306538167 in progress. A previous devel run 38304699092 succeeded.
- Step 26 reading-list import optional not implemented.

## BLOCKED / unresolved verification
- Signed APK/release needs signing credentials.
- Device smoke tests and real AO3/Royal Road/Scribble Hub HTML fixtures not done; their source selector tests use synthetic fixtures.
- Step 10 extension repository hash generation action pending.
- Step 30 newest full R8 result pending. Step 31 latest run pending.

## Next exact operations
1. Tunnel to Surface FIRST in next user turn. GitHub writes only through tunnel.
2. Check runs 38306030991, 38306667542, 38306538167. Repair failures on latest cumulative branch before merge.
3. If Step 30 CI green, fast-forward devel to e7a4e0025 after checking ancestry. Step 31 only after green and/or MediaSession follow-up.
4. Work on Media3 session/background playback, OPDS navigation and support for non-EPUB formats, and source site fixtures.
5. Keep explicit DONE / IN PROGRESS / BLOCKED in response. A pushed commit does NOT mean complete. Preserve .release-temp/ and source-audit/.
