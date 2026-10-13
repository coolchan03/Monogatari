# Monogatari final numbered batch: Steps 67–74 — October 13, 2026

This batch is an IMPLEMENTATION SWEEP, not final acceptance or a production release. User data, authentication and existing backups are preserved. Results require exact-SHA CI and Android testing.

- **67 Novel TTS — PARTIAL:** Android TextToSpeech wired into the local plaintext/HTML reader with a read/stop action and sequential paragraph narration; shuts down when reader closes. No highlighted paragraph, sleep timer or background service; EPUB reader not integrated.
- **68 Video player extras — PARTIAL:** MediaReaderActivity now exposes Skip intro (+85 seconds), 1.25x and 1.0x speed menu controls. Duration bounds checked. PiP, persisted per-show speed and auto-next are still outstanding.
- **69 Release calendar — PARTIAL:** pure ReleaseRhythmEstimator calculates a bounded weekly ETA from chapter-date intervals with unit tests. No chapter database query or calendar view yet.
- **70 Cross-media links — PARTIAL:** CrossMediaRelationPolicy maps AniList relation/media kinds to searchable watch/read/sequel actions. No graph fetching or details buttons yet.
- **71 Crash issue report — PARTIAL:** crash dialog gets explicit Report issue on GitHub button with app/Android context, without attaching private source titles or unreviewed stack traces. Existing ACRA submission remains separate. This is NOT a 6000-character sanitized stack attachment yet.
- **72 Exports — PARTIAL:** CsvExportFormatter produces UTF-8-compatible CSV rows with RFC-like quoting and spreadsheet-formula neutralization; regression test. No SAF save UI, database extraction, Goodreads or StoryGraph mapping.
- **73 Extra source pools — DEFERRED OPTIONAL:** intentionally no blanket Mihon/Aniyomi/CloudStream imports; must test missing source gaps, license/dependency/security compatibility first.
- **74 Release checklist — NOT EXECUTED:** do not claim completion or publish production APK. Requires green build, correct versioning, device smoke tests, backups, cross-device sync, and opt-in publication.

The next phase is acceptance/finalization: revisit incomplete Step 1–73 functionality and test on physical devices. Do not count these eight as completed steps.
