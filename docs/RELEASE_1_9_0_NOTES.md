# Monogatari 1.9.0 — Modernization, safer extensions, two repaired sources

## Updates

- **Libraries.** Kotatsu parser library updated to `68f3fed395` (no source IDs were removed, 15 added), OkHttp 5.5.1, NewPipeExtractor v0.27.0.
- **Safer JavaScript extension updates.** An installed extension now only updates from the repository it came from. A newer version from a different publisher is shown as "Publisher changed" and needs your approval; it is never installed silently.
- **Two repaired sources.** New SkyMangas and Rocks Manga JavaScript extensions (the built-in parsers no longer matched those sites). They come from a new Monogatari-maintained source repository that is added to the recommended manga repositories.
- **Reader and downloads.** Obsolete page prefetching is cancelled when you jump to another place; "next/previous chapter" stays within the current scanlator group; reading progress is no longer overwritten by chapter recovery; `Retry-After` dates are honoured and download retries back off.
- **Search.** A source that does not answer within 30 seconds no longer holds up the rest of a multi-source search.
- **Backups.** Mihon "Local source" titles are reported as needing their files imported separately instead of being listed as missing extensions.
- **Security.** Credentials are removed from debug logs; source WebViews only follow the source's own host (no look-alike hostnames); release workflows pin actions to reviewed commits and use least-privilege permissions.

## Known limitations

- **Not tested on a device.** Everything here is covered by unit tests and a CI build only. Playback on the new NewPipeExtractor, TLS and Cloudflare behaviour on the new OkHttp, and the reader changes have not been exercised on a phone or tablet.
- **Lint did not complete** for this release.
- The new SkyMangas and Rocks Manga extensions were checked against the live sites' APIs in a test harness, not inside the app. Rocks Manga has no real "popular" ordering (the site ignores sorting).
- Softkomik, Temple Scan, Catharsis World and MangaCloud are still broken, and many sites that sit behind Cloudflare challenges need interactive verification. Details: `docs/SOURCE_COMPATIBILITY_STATUS.md`.
- Chapter navigation no longer jumps into another scanlator group at the end of a group; it stops.
