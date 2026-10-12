# Monogatari live work checkpoint

Last observed local time: 2026-10-12 15:17 CDT, America/Chicago. This is last-known observation, NOT a detected timeout.

Rules: Tunnel to Surface FIRST on every new user work turn. Only tunnel may write GitHub, run Git commands, or release. Group steps in longer 20-30 minute batches when possible, with intermediate commits. Preserve .release-temp/ and source-audit/ untracked folders.

Verified default devel: 3814b2c02 (Steps 1-20 base) with prior CI 38300577106 SUCCESS. Most recent devel CI 38302106275 still running.

Step 20 artwork branch commit 2ab3320e7 CI 38301683750 SUCCESS. Not yet separately merged because inherited source work is still being verified.

Step 24 native Gutenberg and shared EPUB commit 58ca805ce CI 38301501700 SUCCESS. Later source headers and richer source work have not yet passed.

Step 25 cumulative Internet Archive: branch batch-25-internet-archive tip 2b12e7360; CI 38302821188 in progress at last observation (unit tests). Includes books EPUB, audio MP3/video MP4/OGV, comics CBZ, bounded extract/cache, stable IDs, source registration/preset handling and compatible API User-Agent. No device playback validation. Earlier superseded run 38302529735 was canceled automatically, not code failure. Do not merge without latest successful full CI.

Step 26 Open Library native source: branch batch-26-open-library tip aee4af79a, includes OpenLibraryMapping.kt/OpenLibraryRepository.kt, BuiltinSource.OPEN_LIBRARY, factory registration, defaults to public-readable search, trending list unfiltered (because availability not returned), metadata, and delegation for available IA EPUB copies via ArchiveRepository ContinuousMediaProvider. Commits 0d8fee0c7 and aee4af79a. Latest CI run 38303193396 QUEUED. Borrowable/unavailable titles are not fully supported; optional metadata cache/want-to-read import not implemented. Verify compile/test and repair any errors before merge.

Live API checks via tunnel: Open Library search for Pride and Prejudice returned three records, two public with IA ids, one borrowable. Internet Archive audio metadata returned one MP3. Gutendex required compatible User-Agent, addressed in source branch. No device tests or signed APK release yet.

Current local: C:\Users\coolc\Codex Projects\Manga-Reader on branch batch-26-open-library, clean except preserved untracked user folders.

NEXT EXACT OPERATION: through Tunnel to Surface, gh run list --repo coolchan03/Monogatari --workflow "Build check" --limit 10. Inspect 38302821188 and 38303193396; if newest fails use gh run view <id> --log-failed and repair latest cumulative branch. Merge ONLY after green. Then source verification on real device and Step 26b book metadata cache, or Step 27 AO3 parser with saved fixtures. Document code vs device tests distinctly.

Outstanding from original guide: signed release Step 6 keystore/secrets, external device verification Step 7, Step 10 extension repo SHA-index generation, Archive PDF fallback / CBZ runtime testing, Open Library borrowing flows and metadata enrichment.
