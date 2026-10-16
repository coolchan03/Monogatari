# Monogatari 0.13.12 — public-books publication year support

Continues the Android-only 83-item finishing roadmap, after the extension-source transparency branch.

## Implemented
- Global Books search retains real `publicationYear` supplied by supported public
  book-catalog APIs, instead of silently omitting public libraries when a year
  filter is active.
- Year range filters respect `Include unknown years`; without a supplied
  year the source's item is explicitly unknown, never assigned a guessed year.
- Public books sort oldest/newest with unknown dates last and stable source
  order for ties; the result row explicitly says `Published YEAR` when known.
- Manga results keep distinct estimated-year badges (`~YEAR` for guesses).
- Plex movies/shows no longer misleadingly place the release year in the
  author's field. Real Plex year metadata appears as `Release year: YEAR`
  in descriptions.
- JVM tests check year bounds, relevance ordering, oldest/newest sorting,
  unknown dates, and source-supplied Plex release years.

## Release criteria
- Actual Android code completion of roadmap #29 is pending the exact-commit
  GitHub build and cloud Android startup smoke tests.
- Real-provider metadata and physical-device screen checks are reserved for
  the user's first v1.0.0 two-device acceptance test; tests may reveal patches.
- Source-provided dates can still be inaccurate, but Monogatari does not
  invent years to fill empty metadata.
- No Android SDK or emulator is installed on the user's laptop or devices.

**Provisional roadmap:** 69 DONE, 14 PARTIAL, 0 PENDING (83 Android items).
