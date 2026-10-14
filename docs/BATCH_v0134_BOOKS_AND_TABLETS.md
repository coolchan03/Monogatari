# Monogatari v0.13.4 — Books, source provenance, and QA

Parent: [88-item tracker](ROADMAP_88_TRACKER.md), single development branch `devel`.

## Implementation
- Books Catalog: "Search all catalogs" queries Gutenberg, Open Library, Internet Archive, Wikisource and Google Books concurrently with partial-failure reporting.
- Google Books returns public metadata and permitted official preview links only. It does not bypass buying, borrowing, DRM or account authentication.
- Official API metadata now includes book covers, publication year where known, language and format/availability descriptions.
- Cross-catalog duplicates merge only when both normalized title and author match; all provider links remain selectable.
- Oldest/newest ordering uses known publication years and puts unknown dates last. It does not invent dates.
- Provider result cards display a real cover thumbnail where supplied, with an accessible text fallback; one provider's errors do not hide the others.
- Release adds four new JVM checks for dedup, provider preservation, sorting and unknown-year handling.
- Guided QA adds three specific spotlight tests for cover data, cross-catalog search and oldest/newest ordering.

## Automated gates
- `testDebugUnitTest` and `assembleDebug`, plus unsigned R8 release assembly.
- Android 15 fresh-start on phone-sized emulator **and** simulated 600dp+ tablet layout.
- Save separate emulator screenshots and crash logs for both sizes as a GitHub Actions artifact.
- This is not a substitute for testing the Books Catalog's provider APIs on device and through real networks.

## Still incomplete
- In-reader borrowing/DRM for protected titles is not supported. Official listings/previews link to the legitimate provider.
- Some providers may require keys, throttle requests, or be inaccessible by region. Such providers are reported as unavailable, not silently considered healthy.
- OPDS 2.0 features, two-pane reader layouts, artwork-preserving OCR inpainting and native Windows remain Partial/Pending in the 88-item tracker.
- Preserve user-installed source extensions, favorites, downloads and reading progress; don't clear app data to update an APK.
