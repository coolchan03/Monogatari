# Monogatari: 83-item implementation tracker

Source: [Full 83-item roadmap](FINISHING_ROADMAP_20261013.md)

Legend: DONE = implemented in code; PARTIAL = incomplete or awaiting real-provider validation; PENDING = not started. Device QA is separate.

**Android-only implementation status (2026-10-15):** This list contains 83 active milestones. The abandoned Windows desktop/PWA project was removed rather than carried as five dropped entries. Status: 60 DONE, 23 PARTIAL, 0 PENDING. New public book catalogs and official-site discovery are being integrated; live provider/device tests are tracked separately.

**Finishing pass 2026-10-14 (second run):** translation (#24-26, #21/#23 improved), Explore/Discover (#8), library Continue (#15), desktop drawer (#62), accent colours (#71/#73/#74), Wikisource, Odysee, Jellyfin, direct links, AO3 filters, global search order, ported-components docs. Details and what is left: `docs/RUN_REPORT_2026-10-14.md`. Device QA is still separate.

**Claude pass 2026-10-14 (first run):** fixed the broken devel build (missing Standard Ebooks button), and implemented #27, #51, #64, #65, #67, #68, #81 and #83; #62 advanced. Large-screen audit findings and fixes: `docs/LARGE_SCREENS.md`.

| # | Status | Requirement |
|---:|---|---|
| 1 | DONE | Remove the inherited Kotatsu-Redo sponsorship prompt from community onboarding. |
| 2 | DONE | Remove its linked sponsor entry from community Settings without dropping upstream attribution. |
| 3 | DONE | Make source scanning keep the screen awake only while Source Repositories or Test Center is visible and the sweep is running; restore ordinary display timeout on stop, pause and navigation. |
| 4 | DONE | Add oldest-first and newest-first estimated publication order within source-result sections, alongside the current year-range filtering. Undated results stay last. |
| 5 | DONE | Silence Android framework ExplicitGcViolation debug warnings without silencing other StrictMode diagnostics. |
| 6 | DONE | Add dedicated tests for conservative publication-year guessing. |
| 7 | DONE | Make the media types explicit on the home screen: Manga/Comics, Books/Novels, Anime/Video, Audio, News. |
| 8 | DONE | Explain or consolidate Explore vs Discover: Explore currently browses source websites, Discover is AniList-driven title discovery. Proposed: **Browse** (source/catalog browsing) and **Discover** (personalized recommendations across media), cross-linked. **Done:** Discover now explains itself and every section ends with "Browse source websites" (opens the Explore tab); Explore's menu has "Discover recommendations". The tab names were not renamed. |
| 9 | DONE | Present clear media selection on every global-search entry point. |
| 10 | DONE | Retain the one global search instead of duplicating searches per media type. |
| 11 | PARTIAL | The More screen now uses the app's shared Material/Settings row language and grouped headings; specialized screens still need a final common toolbar/navigation audit. |
| 12 | DONE | Replaced oversized book-like More tiles with compact Settings-style destination rows, grouped category headings, one-column phone and two-column wide-screen layouts, with screen-reader descriptions and accessible touch targets. Physical appearance review is a separate QA check. |
| 13 | DONE | Redesign News & Followed Tags: readable feed, topic chips, follow/unfollow, saved stories, newest-first and source filter. **Done:** topic chips, site filter, newest/oldest order, saved stories, followed tags. |
| 14 | PARTIAL | Books Catalog shows covers/fallbacks, authors, year, language, source and format with explicit Open EPUB / View listing buttons and provider selection. Series detail, borrowing availability, and cross-provider metadata completeness remain to audit. |
| 15 | DONE | Redesign Unified Library: Manga, Books, Videos, Audio tabs and All; unified folders/collections; progress and continue-reading/watch. **Done:** tabs, Continue strip, and your own collections across all media (long-press a title; chips filter). |
| 16 | DONE | Show source health (working, limited, unreachable) and the last successful scan without noisy alerts. **Done:** More > Source activity log shows Working / Limited / Unreachable per source with when it last worked. In-memory, no alerts. |
| 17 | PARTIAL | Books Catalog now restores query/provider after Activity recreation, remembers the last query, displays skeleton result cards and preserves per-provider errors. Other search and specialized screens still need equivalent restoration/skeleton coverage. |
| 18 | PARTIAL | Consistent typography, controls, dark mode, edge-to-edge behavior and accessibility descriptions. |
| 19 | DONE | Preserve the unified list and private-category visibility settings. |
| 20 | DONE | Keep ML Kit OCR for Japanese/Chinese/Korean/Latin, translate locally, then overlay English directly onto original page. |
| 21 | PARTIAL | Segment speech bubbles/panels rather than covering arbitrary rectangular image regions. **Improved:** nearby OCR regions are merged into one bubble (right-to-left columns for vertical CJK). True bubble-shape segmentation is not done. |
| 22 | DONE | Replace flat-color patches with background-aware filling/inpainting when feasible. **Done (smooth fill):** shaded or gradient bubbles are filled from their four edge colours instead of one flat patch. Not true inpainting of artwork. |
| 23 | PARTIAL | Fit text within detected bubbles (vertical/horizontal text, fonts, line breaks, alignment). **Improved:** balanced line breaking, hyphenation, and text that cannot fit grows the bubble up/down within the page. Vertical text layout and font choice not done. |
| 24 | DONE | One-tap translated/original toggle; optional side-by-side view. **Done:** translated-page viewer with Show original / Show translation. Side-by-side view not done. |
| 25 | DONE | Per-series preferred source language and target language, with English override. **Done:** reader options > "Translation languages for this series" (source and target, saved per series). |
| 26 | DONE | Show OCR confidence and allow tap-to-correct wrongly recognized text. **Done:** recognition confidence is stored per bubble; doubtful lines are counted and tapping any bubble shows the recognised text with its confidence and lets you type a correction that is saved and redrawn. |
| 27 | DONE | Cache translations per page and target language; offer offline model download management and cache limits. *(Settings > Data cleanup: "Clear translated pages" and "Translation languages"; 200-page cap.)* **Done:** the downloaded packs can be listed and deleted from reader options (new shortcut) and Settings > storage; translated pages are cached (600-page limit). |
| 28 | DONE | Never send private pages to a server unless a separately enabled cloud provider is explicitly authorized. |
| 29 | PARTIAL | Improve reliable publication metadata: year from source APIs/structured fields, then mark estimated years distinctly. |
| 30 | DONE | Offer oldest/newest globally in addition to within-source sorting once normalized metadata can be stored. **Done:** "all sources together" oldest/newest choices in Publication years merge every source into one list. |
| 31 | DONE | Support date ranges (e.g. 1990-2000), full years, unknown dates, and optional date confidence. **Done:** year range (existing), plus "include titles with no known year"; years are estimates. Per-title date confidence not shown. |
| 32 | PARTIAL | Offer genre/demographic tags, language, reading direction, format and progress filters across media types. |
| 33 | DONE | Install source presets: Manga, Novels, Audiobooks, Video, and Multi-media. **Done:** Multi-media preset added to the first-run presets. |
| 34 | PARTIAL | Display new/updating/broken extension status, permissions, source language and attribution. |
| 35 | DONE | Keep Mangayomi JavaScript extensions first-class; do not claim a Dart extension works without a compatible runtime. |
| 36 | DONE | Per-source log and open-in-browser fallback when a site changes or requires legitimate sign-in. **Done:** Source activity log with Open a source's website and Copy log; lists already offer Open in browser on errors. |
| 37 | PARTIAL | Cross-source duplicate-title merging and preferred-source selection. **Improved:** Books catalog deduplicates sources and remembers a preferred provider per title; global year-sort merging exists. Regular manga per-source sections and per-title source preference remain unfinished. |
| 38 | DONE | Standard Ebooks: searchable official public book catalog (without patron-only OPDS access), cover art and author metadata, unified Books search, and per-book verified compatible EPUB download links resolved on demand. Provider page/device verification remains for user QA. |
| 39 | DONE | Wikisource - multilingual public-domain and freely licensed works. **Done:** English Wikisource through the MediaWiki API (search, featured texts, chapters from sub-pages). |
| 40 | PARTIAL | Faded Page — official search endpoint, title/author/year/cover metadata and official reading links; device verification pending. |
| 41 | PARTIAL | Project Gutenberg Australia — live official CSV catalog, title/author lookup, HTML/text links and regional copyright warning; device verification pending. |
| 42 | PARTIAL | Global Grey — searchable official sitemap with safe book/online-text links; device verification pending. |
| 43 | DONE | Entertainment-focused public-domain fiction catalogs - fantasy, romance, horror and science fiction with legal EPUB/PDF links. **Done:** Project Gutenberg search takes `genre:horror`, `genre:fantasy`, `genre:romance`, `genre:"science fiction"` (EPUB links). |
| 44 | DONE | Fanfiction discovery - fandom, ship, language, rating and story-update filters across installed sources. **Done:** AO3 search accepts fandom:, ship:, rating:, lang: and updated: tokens (limited by your content level). |
| 45 | PARTIAL | StoryWeaver — official-site search from Books, but native metadata/reader integration still pending. |
| 46 | DONE | Google Books - metadata and legally offered previews only, not bypassing access limits. **Done:** labels say exactly what is allowed (free EPUB, free to read, preview, listing only); official free EPUB downloads; free titles first. |
| 47 | PARTIAL | Baen Free Library — actual free-library catalog cards with covers and official book links; device verification pending. |
| 48 | PARTIAL | FictionPress and FanFiction.net — official-site search available, but the sites require browser verification; native metadata parsing is pending. |
| 49 | DONE | Library OPDS 1.2/2.0 support, including user-supplied catalogs, paging, searching and authentication. **Done:** OPDS 1.x Atom and 2.0 JSON, next-page paging, catalog search (OpenSearch), covers, and password catalogs (https://user:password@host/path). |
| 50 | PARTIAL | Vimeo public-video search via user-supplied API token is implemented; source links out to official Vimeo playback because direct streams are not provided. Channel/collection browsing and credential-backed device QA remain. |
| 51 | DONE | Dailymotion public videos and channels. *(Built-in source: public Data API + HLS player metadata; needs device playback check.)* |
| 52 | DONE | Internet Archive Moving Image collection with direct public media files. |
| 53 | PARTIAL | Bilibili — built-in public video search/rankings source now returns titles, creators and thumbnails using provider metadata; playback opens the official site and physical-device/region validation is still pending. |
| 54 | DONE | Odysee public videos and channels; verify licensing and streams. **Done:** Odysee public free videos via the LBRY claim_search proxy; mature tags excluded. |
| 55 | DONE | Twitch public channels, live discovery, VOD and clip listings use the authorized Helix API and construct official Twitch playback URLs; clip IDs are validated and deduplicated. Credential/live-device QA follows with the user's first official release test. |
| 56 | DONE | Independent films, anime trailers, fan videos and entertainment video collections with permitted playback. **Done:** `collection:` search for curated Internet Archive collections (feature films, silent, cartoons, classic TV, trailers, noir, community/fan films, old-time radio). |
| 57 | DONE | Jellyfin home-server libraries through the supported server API (user's own server). **Done:** Jellyfin on your own server: More > Jellyfin server (address + API key), then the Jellyfin source. |
| 58 | PARTIAL | Plex server/token settings, recent/search libraries, show episodes and direct-play with token headers are implemented. Real-device codec/stream/access and private-server testing remain. |
| 59 | DONE | Direct HLS/DASH files and permitted M3U playlists, with visible stream/codec diagnostics. **Done:** "Direct links" source: paste an .m3u8 / .mpd / video-or-audio file / .m3u playlist link into video search; quality names show resolution, codecs and bitrate. |
| 60 | DONE | Podcast/video RSS subscriptions, suggested feeds, episode playlists, long-feed parsing, durations, chapter JSON and transcript links; new HTTPS-only in-app Media3 player with caption sidecars, chapter seeking, resume positions, accessible Play buttons and external-player fallback. Provider and physical-device playback compatibility must still be tested. |
| 61 | DONE | Do not attempt to bypass DRM/paywalls; link out to official paid services when in-app playback is unavailable. |
| 62 | DONE | Adaptive navigation rail on tablets, side drawer on desktop widths, bottom bar on phones. *(Rail now on every window >= 600dp wide, portrait included; desktop drawer still open.)* **Done:** windows at least 1000dp wide start with the labelled, expanded side drawer (collapsible). |
| 63 | PARTIAL | Two-pane library/search/details interface at widths >= 600 dp and a three-column desktop layout where practical. |
| 64 | DONE | Responsive book/video grids, meaningful max text width and expanded reader controls for 13-inch tablets. *(Width-based columns, 760dp text column, rotate controls hidden where Android 16 ignores them.)* |
| 65 | DONE | Proper portrait large-screen resources, not just landscape 600dp alternatives. *(-w600dp-land moved to -w600dp; details keeps landscape-only layout with working rating controls.)* |
| 66 | PARTIAL | Resizable/freeform multiwindow, foldable posture and rotation-state testing. |
| 67 | DONE | Keyboard shortcuts, mouse scroll and hover, right click/context menu and D-pad navigation. *(Ctrl+F or / search; arrow/Page/Space/Home/End in PDF, EPUB and text readers; existing reader keys, mouse wheel and context clicks.)* |
| 68 | DONE | Drag/drop local EPUB, PDF, CBZ, images and supported media. *(Drop EPUB/PDF/text onto Books; CBZ/images still use the manga local import.)* |
| 69 | PARTIAL | Settings-like More rows use scalable text and minimum 72dp row targets; catalog action buttons use 48dp tap targets and descriptive accessibility labels. Full screen-reader/font-scaling/touch-target device audit remains. |
| 70 | DONE | Android cold-start compatibility is checked on temporary GitHub-hosted runners; no emulator or SDK is installed on user devices. |
| 71 | DONE | Keep existing light/dark and theme chooser; add a user-picked accent color. **Done:** Settings > Appearance > Accent colour (Android 12+). |
| 72 | DONE | Add multicolor mode with a restrained palette mapped to Books/Manga/Video/News, not random colors per widget. |
| 73 | DONE | Offer palette presets: ocean-teal, vaporwave teal-purple, pastel, monochrome and system dynamic color where available. **Done:** presets Theme default (system/dynamic), Ocean teal, Vaporwave, Pastel, Monochrome, plus any #hex. |
| 74 | DONE | Preview color palette live and preserve contrast/readability in dark mode. **Done:** live preview in the current light/dark mode and a low-contrast warning; Material generates the full scheme from the seed. |
| 75 | DONE | Replace the inherited bear identity with an original mascot, after choosing among a teal fox with bookmark-shaped tail, a glow-moth or a star-tailed tanuki. **Done:** Kitsune (anime spirit fox, form per media type, colour + line-art sets, hidden muscular human-with-fox-ears Easter egg) replaces the Kotatsu launcher/Play Store/legacy icons, widget previews and empty-state art. See `docs/MASCOT_IDEAS.md`. |
| 76 | DONE | Use the mascot sparingly (app icon, empty state, first-run tips), not as intrusive decoration. **Done:** launcher icon and splash, library empty states (a form per tab), list empty states (line art), Easter egg. First-run tips not yet. |
| 77 | DONE | Daily meaningful-change watch for Kotatsu-Redo, kotatsu-parsers-redo, Mangayomi and relevant source/runtime projects. |
| 78 | DONE | Document upstream revision, license, modifications and tests for each ported component. **Done:** `docs/PORTED_COMPONENTS.md`. |
| 79 | DONE | Audit updates before merging; do not silently overwrite local additions with copied upstream files. **Done:** audit gate and local-only file list in `docs/UPSTREAM_REVISION_LEDGER.md`. |
| 80 | PARTIAL | Android CI: unit tests, assembler/R8 and actual emulator cold-start + basic navigation smoke checks. |
| 81 | DONE | Tablet emulation screenshots and layout checks; manual device QA only after CI. *(CI cold-starts on phone, portrait-tablet and landscape-tablet windows and keeps screenshots.)* |
| 82 | DONE | Clearly distinguish debug preview, release signing and verified device-tested version. |
| 83 | DONE | Preserve user data, backups, settings and reading progress across updates. *(Automatic pre-update copy of database + settings, last two kept; Settings > Backup > Undo an app update.)* |
