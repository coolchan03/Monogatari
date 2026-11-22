# Monogatari changelog

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
