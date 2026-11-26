# Monogatari changelog

## v1.11.2-rc1 - Prerelease: parser update, web hardening (2026-11-26)
- Includes everything in v1.11.1-rc1 (the 1.11 line is now merged with the PWA/Windows work in `devel`).
- Parser library updated to kotatsu-parsers-redo `8e4b04f3df` (DrakeScans rewrite, MgKomik parser, AtsuMoe page ordering, MangaWorld domain updates). Deep links added for drakecomic.net, mangaworld.me and mangaworldadult.org. Not tested against the live sites.
- Web/Windows app: relay requires an allowed Origin when configured and re-checks every redirect; Electron IPC and navigation checks; extension updates stay with the publisher they were installed from and cross-publisher switches need approval.
- Prerelease; see docs/RELEASE_1_11_2-rc1_NOTES.md.

## v1.11.1-rc1 - Prerelease: sync merge fix and import safety (2026-11-22)
- Sync: a favourite removed on one device no longer returns from another; rows changed during a sync are kept (ported from Kotatsu-Redo).
- Titles imported from Mangayomi without their extension keep their source name instead of becoming "unknown".
- Opt-in live smoke tests for parser sources and JS extensions; live results recorded in the release notes.
- Prerelease; see docs/RELEASE_1_11_1-rc1_NOTES.md.

## v1.11.0 - Parser update and library safety (2026-11-22)
- Parser library updated to kotatsu-parsers-redo `b049587f96` (source repairs, new sources). Not tested against the live sites; some sources may still need attention.
- Sources dropped by the new parsers keep working as saved titles: your favourites and history stay, shown as "(retired)", and can be moved with Alternatives.
- Source migration now carries reading statistics and per-title settings (custom cover, title, reader mode, colour filter).
- Chapter lists flag possibly missing chapters; Continue is hidden when the last-read title is finished; the Library reopens on the last category tab.
- Suggestions: "Not interested" with Undo (kept on this device only); equivalent genre labels are matched together.
- NewPipeExtractor v0.27.1.
- Device verification outstanding. See docs/RELEASE_1_11_0_NOTES.md.

## v1.10.0 - Adaptive search preview (2026-11-13)
- Search preview below the results on tablet portrait, beside them in landscape, adjustable and hideable; richer preview with lazy details.
- Manga/Webtoon/Comics/Manhwa/Manhua format filter; in-place search-kind switching; Material filter dropdowns.
- JS extension tests now run QuickJS on the JVM; SkyMangas and Rocks Manga flow tests.
- Device verification and lint still outstanding. See docs/RELEASE_1_10_0_NOTES.md.

## v0.12.0 - Source Setup and Website Fallback
- First-run JS source onboarding, background install progress and manual retry.
- Targeted extension repair after repeated failures; safe website grouping and search fallback.
- Guided regression tests for source onboarding, grouping and recovery.
- Debug testing release only; Android device verification still required.


## v0.11.0 - Source Reliability Preview (2026-10-12)
- Persistent per-source health with non-destructive migration and unhealthy-source warnings.
- One-time editable media presets; no replacement of existing presets.
- Staged JavaScript updates, rollback safeguards, scheduled checks and manual check controls.
- Extended guided Test Center for source diagnostics and compatibility.
- Public debug-signed test APK only; device verification and production signing remain outstanding.
