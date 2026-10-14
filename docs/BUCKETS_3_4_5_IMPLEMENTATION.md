# Monogatari — Buckets 3, 4, 5

Based on `ccr-94e2bfd8-3w4etd` at `46f1db651` (October 14, 2026). Applies on top of the previously saved Baen starter patch.

## Bucket 3: sources and coverage

- Baen Free Library: publisher-owned free-library listing search integrated in the Books catalog; free listings only, official publisher links, no unofficial downloads.
- Global Grey, Project Gutenberg Australia, Faded Page, FanFiction.net and FictionPress added as visibly labeled **official external catalogs**. They are *not* fake built-in parser implementations.
- Existing authenticated OPDS catalogs: same-origin HTTPS authorization for nested pages, OpenSearch, and EPUB acquisitions; no secret forwarding to other hosts, subdomains, HTTP, or alternate ports.
- Saved OPDS catalog labels hide embedded credentials; the actual user-entered URLs are unchanged in private preferences and remain eligible for normal device backups.
- Existing Manga, Books/Novels, Video, Audio built-in and JS extensions remain available; no stable source ID changes.

## Bucket 4: visual/UI consistency

- More screen searchable by title, subtitle and section; section headings survive filters only when matching cards exist.
- More uses strongly typed header/destination entries, eliminating mixed `Any`/`String` runtime casts.
- More and Source activity log share Material card style, headings and accessibility semantics; Settings keeps its own existing searchable adaptive preference layout.
- Books has official-source browse chips using the app's existing Material design, no new third-party UI packages.

## Bucket 5: cleanup, QA, release readiness

- Source activity error logs redact bearer tokens, API keys, URL credentials and Plex tokens **before retaining and copying logs**.
- Unit tests cover Baen parser restrictions, OPDS same-origin authentication, source log secret redaction, and More search grouping.
- Automated CI runs Python XML/source wiring guardrails before JVM tests, debug APK, unsigned R8 release, and phone/tablet emulator smoke tests (existing workflow).
- Test Center adds guided checks for new sources, credentials and More navigation; the stale "mascot-free" instruction is corrected.
- Patch is atomic and guarded: it stops before overwriting any already-existing file or unrecognized source anchor.

## Boundaries / must remain honest

- New official catalog links are not integrated in-app content extractors; avoid claiming otherwise.
- Twitch, Plex, Vimeo and Jellyfin require the user's on-device credentials to verify real playback.
- Web sources may change their HTML/terms; test catalog requests on real devices and respect content rights.
- Requires a successful GitHub Actions Android build and manual phone/tablet QA before considering buckets finished as a release.
- GitHub publication must happen through Tunnel to Surface; this local patch does not push anything.

## Apply, verify and roll back

```bash
python apply_finish.py /path/to/Monogatari            # preflight only
python apply_finish.py /path/to/Monogatari --apply    # writes all files atomically
python scripts/qa/verify_bucket345.py                 # after applying
# in a git checkout, use git diff --check; git diff; run CI only after Tunnel to Surface reconnects
```

No Android SDK, local credentials, or tokens are installed or required by this patch. The patch does not touch the existing library database, backup settings, stable IDs, or release signing.
