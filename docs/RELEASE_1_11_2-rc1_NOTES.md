# Monogatari v1.11.2-rc1 (prerelease)

Android 1.11.1-rc1 merged into the `devel` line that carries the PWA and Windows app, plus a parser update and web security fixes.

## Changes
- Parser library `8e4b04f3df` (was `b049587f96`): DrakeScans, MgKomik, AtsuMoe and MangaWorld updates from upstream. Source names are unchanged, so saved titles, history and backups keep working.
- Manifest deep links for the new MangaWorld and DrakeScans domains.
- Web relay and Windows app hardening; web extension publisher pinning (see CHANGELOG).

## Verification
- Automated: GitHub CI unit tests, debug and release builds for the commit named below.
- NOT done: device testing, live-source testing of the updated parsers, Softkomik/MangaDotNet/Ikiru and other source repairs (they need changes in the external parser library), download-queue and reader work.
- See docs/SOURCE_FIX_TRACKER.md.
