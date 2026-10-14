# Monogatari: 88-item implementation tracker

Source: [Full 88-item roadmap](FINISHING_ROADMAP_20261013.md)

Legend: DONE = code exists; PARTIAL = unfinished; PENDING = not implemented. Device QA is separate.

**v0.13.6 implementation snapshot:** 27 DONE, 36 PARTIAL, 25 PENDING. Newly started: public Standard Ebooks fiction-release browsing (#38) and user-managed entertainment video/podcast RSS (#60). Both still require full device testing. Per the entertainment-only scope, original educational-book/video ideas #43/#44/#56 were replaced by fiction, fanfiction and film-related requirements. No Windows app, full OCR inpainting or mascot yet.

**Claude pass 2026-10-14:** fixed the broken devel build (missing Standard Ebooks button), and implemented #27, #51, #64, #65, #67, #68, #86 and #88; #62 advanced. Large-screen audit findings and fixes: `docs/LARGE_SCREENS_AND_WINDOWS.md`.

| # | Status | Requirement |
|---:|---|---|
| 1 | DONE | Remove the inherited Kotatsu-Redo sponsorship prompt from community onboarding. |
| 2 | DONE | Remove its linked sponsor entry from community Settings without dropping upstream attribution. |
| 3 | DONE | Make source scanning keep the screen awake only while Source Repositories or Test Center is visible and the sweep is running; restore ordinary display timeout on stop, pause and navigation. |
| 4 | DONE | Add oldest-first and newest-first estimated publication order within source-result sections, alongside the current year-range filtering. Undated results stay last. |
| 5 | DONE | Silence Android framework ExplicitGcViolation debug warnings without silencing other StrictMode diagnostics. |
| 6 | DONE | Add dedicated tests for conservative publication-year guessing. |
| 7 | DONE | Make the media types explicit on the home screen: Manga/Comics, Books/Novels, Anime/Video, Audio, News. |
| 8 | PARTIAL | Explain or consolidate Explore vs Discover: Explore currently browses source websites, Discover is AniList-driven title discovery. Proposed: **Browse** (source/catalog browsing) and **Discover** (personalized recommendations across media), cross-linked. |
| 9 | DONE | Present clear media selection on every global-search entry point. |
| 10 | DONE | Retain the one global search instead of duplicating searches per media type. |
| 11 | PARTIAL | Use one shared card/toolbar/nav system for More, Settings, and all specialized screens. |
| 12 | PARTIAL | Replace the current special-purpose More screen's card list with the same design language and spacing as Settings. |
| 13 | PARTIAL | Redesign News & Followed Tags: readable feed, topic chips, follow/unfollow, saved stories, newest-first and source filter. |
| 14 | PARTIAL | Redesign Books Catalog: covers, author/series/year/language, providers, formats and download/borrow/open buttons. |
| 15 | PARTIAL | Redesign Unified Library: Manga, Books, Videos, Audio tabs and All; unified folders/collections; progress and continue-reading/watch. |
| 16 | PARTIAL | Show source health (working, limited, unreachable) and the last successful scan without noisy alerts. |
| 17 | PARTIAL | Use thoughtful empty states, per-source error explanations, search-state restoration and loading skeletons. |
| 18 | PARTIAL | Consistent typography, controls, dark mode, edge-to-edge behavior and accessibility descriptions. |
| 19 | DONE | Preserve the unified list and private-category visibility settings. |
| 20 | DONE | Keep ML Kit OCR for Japanese/Chinese/Korean/Latin, translate locally, then overlay English directly onto original page. |
| 21 | PARTIAL | Segment speech bubbles/panels rather than covering arbitrary rectangular image regions. |
| 22 | PENDING | Replace flat-color patches with background-aware filling/inpainting when feasible. |
| 23 | PARTIAL | Fit text within detected bubbles (vertical/horizontal text, fonts, line breaks, alignment). |
| 24 | PARTIAL | One-tap translated/original toggle; optional side-by-side view. |
| 25 | PENDING | Per-series preferred source language and target language, with English override. |
| 26 | PENDING | Show OCR confidence and allow tap-to-correct wrongly recognized text. |
| 27 | DONE | Cache translations per page and target language; offer offline model download management and cache limits. *(Settings > Data cleanup: "Clear translated pages" and "Translation languages"; 200-page cap.)* |
| 28 | DONE | Never send private pages to a server unless a separately enabled cloud provider is explicitly authorized. |
| 29 | PARTIAL | Improve reliable publication metadata: year from source APIs/structured fields, then mark estimated years distinctly. |
| 30 | PARTIAL | Offer oldest/newest globally in addition to within-source sorting once normalized metadata can be stored. |
| 31 | PARTIAL | Support date ranges (e.g. 1990-2000), full years, unknown dates, and optional date confidence. |
| 32 | PARTIAL | Offer genre/demographic tags, language, reading direction, format and progress filters across media types. |
| 33 | PARTIAL | Install source presets: Manga, Novels, Audiobooks, Video, and Multi-media. |
| 34 | PARTIAL | Display new/updating/broken extension status, permissions, source language and attribution. |
| 35 | DONE | Keep Mangayomi JavaScript extensions first-class; do not claim a Dart extension works without a compatible runtime. |
| 36 | PARTIAL | Per-source log and open-in-browser fallback when a site changes or requires legitimate sign-in. |
| 37 | PARTIAL | Cross-source duplicate-title merging and preferred-source selection. |
| 38 | PARTIAL | Standard Ebooks - high-quality public-domain EPUB collections/catalog feed. |
| 39 | PARTIAL | Wikisource - multilingual public-domain and freely licensed works. |
| 40 | PENDING | Faded Page / Distributed Proofreaders Canada - public-domain ebooks; check region/rights. |
| 41 | PENDING | Project Gutenberg Australia - regional public-domain library; respect copyright territories. |
| 42 | PENDING | Global Grey Ebooks - historical ebooks, where metadata and formats allow. |
| 43 | PENDING | Entertainment-focused public-domain fiction catalogs - fantasy, romance, horror and science fiction with legal EPUB/PDF links. |
| 44 | PENDING | Fanfiction discovery - fandom, ship, language, rating and story-update filters across installed sources. |
| 45 | PENDING | StoryWeaver - multilingual Creative Commons stories, subject to its API/usage rules. |
| 46 | PARTIAL | Google Books - metadata and legally offered previews only, not bypassing access limits. |
| 47 | PENDING | Baen Free Library - legitimately free titles; check format and access. |
| 48 | PENDING | FictionPress / FanFiction.net - public stories where site rules and scraping restrictions allow. |
| 49 | PARTIAL | Library OPDS 1.2/2.0 support, including user-supplied catalogs, paging, searching and authentication. |
| 50 | PENDING | Vimeo public videos, channels and collections where embedding/playback is allowed. |
| 51 | DONE | Dailymotion public videos and channels. *(Built-in source: public Data API + HLS player metadata; needs device playback check.)* |
| 52 | DONE | Internet Archive Moving Image collection with direct public media files. |
| 53 | PENDING | Bilibili public videos, where regional availability and site restrictions permit. |
| 54 | PENDING | Odysee public videos and channels; verify licensing and streams. |
| 55 | PENDING | Twitch publicly accessible clips/VODs, with appropriate API credentials. |
| 56 | PENDING | Independent films, anime trailers, fan videos and entertainment video collections with permitted playback. |
| 57 | PENDING | Jellyfin home-server libraries through the supported server API (user's own server). |
| 58 | PENDING | Plex home-server libraries through authenticated APIs, subject to provider constraints. |
| 59 | PARTIAL | Direct HLS/DASH files and permitted M3U playlists, with visible stream/codec diagnostics. |
| 60 | PARTIAL | Video RSS and podcast feeds, playlists, chapter markers and subtitle tracking. |
| 61 | DONE | Do not attempt to bypass DRM/paywalls; link out to official paid services when in-app playback is unavailable. |
| 62 | PARTIAL | Adaptive navigation rail on tablets, side drawer on desktop widths, bottom bar on phones. *(Rail now on every window >= 600dp wide, portrait included; desktop drawer still open.)* |
| 63 | PARTIAL | Two-pane library/search/details interface at widths >= 600 dp and a three-column desktop layout where practical. |
| 64 | DONE | Responsive book/video grids, meaningful max text width and expanded reader controls for 13-inch tablets. *(Width-based columns, 760dp text column, rotate controls hidden where Android 16 ignores them.)* |
| 65 | DONE | Proper portrait large-screen resources, not just landscape 600dp alternatives. *(-w600dp-land moved to -w600dp; details keeps landscape-only layout with working rating controls.)* |
| 66 | PARTIAL | Resizable/freeform multiwindow, foldable posture and rotation-state testing. |
| 67 | DONE | Keyboard shortcuts, mouse scroll and hover, right click/context menu and D-pad navigation. *(Ctrl+F or / search; arrow/Page/Space/Home/End in PDF, EPUB and text readers; existing reader keys, mouse wheel and context clicks.)* |
| 68 | DONE | Drag/drop local EPUB, PDF, CBZ, images and supported media. *(Drop EPUB/PDF/text onto Books; CBZ/images still use the manga local import.)* |
| 69 | PARTIAL | Surface accessibility scaling and touch-target checks. |
| 70 | DONE | Short-term: Android emulator for compatibility/testing only; do not mistake it for native Windows support. |
| 71 | PENDING | Investigate a Kotlin Compose Multiplatform Windows client with a shared Kotlin core/parser/runtime and a dedicated desktop UI. |
| 72 | PENDING | First desktop milestone: local EPUB/PDF/CBZ library, login-free search, reading progress and storage. |
| 73 | PENDING | Second: source repositories, network reading, extensions, video playback and optional sync. |
| 74 | PENDING | Explore a browser/PWA interface only for parts where browser CORS, cookies and storage rules allow. |
| 75 | PENDING | Validate Windows file dialogs, background updates, multiwindow, hardware decoding and portable packaging. |
| 76 | PARTIAL | Keep existing light/dark and theme chooser; add a user-picked accent color. |
| 77 | DONE | Add multicolor mode with a restrained palette mapped to Books/Manga/Video/News, not random colors per widget. |
| 78 | PARTIAL | Offer palette presets: ocean-teal, vaporwave teal-purple, pastel, monochrome and system dynamic color where available. |
| 79 | PARTIAL | Preview color palette live and preserve contrast/readability in dark mode. |
| 80 | PENDING | Replace the inherited bear identity with an original mascot, after choosing among a teal fox with bookmark-shaped tail, a glow-moth or a star-tailed tanuki. |
| 81 | PENDING | Use the mascot sparingly (app icon, empty state, first-run tips), not as intrusive decoration. |
| 82 | DONE | Daily meaningful-change watch for Kotatsu-Redo, kotatsu-parsers-redo, Mangayomi and relevant source/runtime projects. |
| 83 | PARTIAL | Document upstream revision, license, modifications and tests for each ported component. |
| 84 | PARTIAL | Audit updates before merging; do not silently overwrite local additions with copied upstream files. |
| 85 | PARTIAL | Android CI: unit tests, assembler/R8 and actual emulator cold-start + basic navigation smoke checks. |
| 86 | DONE | Tablet emulation screenshots and layout checks; manual device QA only after CI. *(CI cold-starts on phone, portrait-tablet and landscape-tablet windows and keeps screenshots.)* |
| 87 | DONE | Clearly distinguish debug preview, release signing and verified device-tested version. |
| 88 | DONE | Preserve user data, backups, settings and reading progress across updates. *(Automatic pre-update copy of database + settings, last two kept; Settings > Backup > Undo an app update.)* |
