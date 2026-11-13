# Monogatari changelog

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
