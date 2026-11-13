# Monogatari 1.10.0 — Adaptive search preview, format filter, runtime-tested extensions

## Updates

- **Search preview that fits the window.** On a tablet held upright the preview now sits **below** the results; in landscape it sits beside them; on phones nothing changes. Drag the handle (or use the arrow keys / TalkBack actions) to resize. Menu > Preview layout: Automatic, List only, List and preview; the preview can be hidden and shown. Nothing is shown until you pick a result.
- **A preview worth the space.** Large cover, alternative title, creators, source, status, language, rating, chapter count, tags, your reading progress and a description with "Show more". Details load when you select a title (a quick second selection cancels the first request). Actions: Read/Continue (Watch/Listen for media), Details, Add to library, and Download, Choose source, Open in browser, Share only where they apply.
- **Format filter in Manga search.** Manga, Webtoons, Comics, Manhwa, Manhua chips, plus "Include format unknown". A title counts as a webtoon only when its own tags say so; nothing is guessed from country or reader mode.
- **Search type changes in place.** Switching name/author/genre no longer reopens the screen, and keeps filters and the selected preview.
- **Material filter dropdowns** replace the old spinners in the search filter dialogs.

## Under the hood

- The JavaScript-extension tests now actually run the QuickJS engine on the JVM (they were silently skipped before). SkyMangas and Rocks Manga are tested through search, details, chapters and pages in that engine.
- No change to the Room schema, backups, source ids, publisher pinning or HTTPS behaviour. Parser library stays at `68f3fed395`.

## Known limitations

- **Not tested on a device or emulator.** The tablet layouts, divider, TalkBack actions and 200% font behaviour were verified only by unit tests and static layout checks. Please run the checklist in `docs/AUDIT_BATCH_REPORT_20261113.md`.
- **Lint did not complete** (it was stopped after 55 minutes with no result).
- The Manga chip is source-declared; some sources mix manga and webtoons, so it can include untagged webtoons.
- SkyMangas and Rocks Manga extensions: tested with canned responses; only the list endpoints were probed live (from a datacenter, not a phone).
- Newer Kotatsu-Redo parsers (signed runtime bundle) are **not** adopted; see `docs/UPSTREAM_REVIEW_20261113.md`. Softkomik, Temple Scan, Catharsis World and MangaCloud are still broken or restricted.
- Links on `kotatsuredo.app` cannot be verified for this app's package (see `docs/SOURCE_COMPATIBILITY_STATUS.md`).
