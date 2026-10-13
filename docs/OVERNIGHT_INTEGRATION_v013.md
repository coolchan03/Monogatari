# Monogatari v0.13.0 integration checkpoint

## Contents
- Step 37: delay hiding extensions removed from a remote index for 30 days, rather than dropping them immediately; includes retirement policy tests.
- Step 38: compare normalized supported website hosts for Kotatsu parsers and JS alternatives and group matching entries in Explore/global search; includes host/fallback tests. Unknown hosts are not force-merged.
- Step 40: respect recent verified `broken` markers from extension indexes; stale, unknown or unverified markers are not enough to suppress a source; includes status tests.
- Step 41: merged backup restore preserves newer history, favourites and categories and tombstone precedence; bookmarks/stats union; device identity metadata.
- Step 42 (partial): optional repository URLs, OPDS catalog URLs and allowlisted parser settings export/restore; preserve local secrets. JS source install metadata and post-restore reinstall remain missing.

## CI and release safety
- Starting published base: v0.12.0, devel `1f91e90210553d6411ca0f1a0123084dcf60aa07`, CI `38329819526` SUCCESS.
- Cumulative Stage H starting commit: `582c45b2feb297a602d9969e7378daaec09f1db8`, Build check `38334785291` SUCCESS.
- Step 37 individual CI: `38333203894` SUCCESS.
- Step 40 app CI: `38332820639` SUCCESS; extension repository's nightly workflow run `38332852658` SUCCESS.
- Stage G/H combination is new: **must have its own exact-commit CI success** (unit tests, debug APK, unsigned R8) before moving devel or publishing v0.13.0.
- Previous Stage H candidates 42a/42b had compilation failures that were fixed in 42c; only rely on cumulative passing head.

## Incomplete and required manual checks
- Step 37: re-test changed repaired sources and retry original call once; 48-hour rollback/30-day retention needs real-device background verification.
- Step 38: automatic migration of dead library titles and safe Undo are **not implemented**. Preserve reading history and do not move titles silently.
- Step 40: extension extraction checks are best-effort; a nightly PASS does not prove every source works or media playback succeeds.
- Step 41: backup newest-wins JUnit tests pass on candidate, but two-device restore is not phone/tablet verified.
- Step 42: JS source metadata installer, source preference coverage and backup encryption still incomplete; archives may intentionally include user identity secrets. Treat backups as private.
- Steps 43-45 (folder sync, WebDAV, foreign imports) not yet implemented.
- Step 6 production signing remains blocked by missing keystore/secrets. APKs currently debug signed; not device tested.
- Numbering: master guide explicitly defines **Steps 1-74** (no Step 75). Do not equate last reached step with full acceptance.
- Leave `.release-temp/`, `source-audit/`, device data, app databases and user libraries unmodified.

## Next batched implementation
1. If CI is red, inspect exact failing task/log, fix only the integration regression, test a new exact commit.
2. On green build, publish debug APK preview; never assert this is production-signed.
3. Continue Step 42 missing JS source restore and Step 43 opt-in folder sync with per-device cursors and merge-safety tests.
4. Record unverified device cases for the morning repair pass.
