# Monogatari — Proposed finishing roadmap (2026-10-13)

**This is a new proposal list for current ideas, not a replacement for the older 75-step implementation tracker.**
**Canonical repository:** coolchan03/Monogatari, `devel` (single active branch).
**Goal:** a distinctive manga, novel, video, and news reader, with reliable source support and a consistent adaptive UI.
**Status:** "Implemented, awaiting CI/device QA" is not the same as "released" or "fully tested."

## Batch implemented in commit 283ede474 (v0.13.2 source; build verification pending)
1. Remove the inherited Kotatsu-Redo sponsorship prompt from community onboarding.
2. Remove its linked sponsor entry from community Settings without dropping upstream attribution.
3. Make source scanning keep the screen awake only while Source Repositories or Test Center is visible and the sweep is running; restore ordinary display timeout on stop, pause and navigation.
4. Add oldest-first and newest-first estimated publication order within source-result sections, alongside the current year-range filtering. Undated results stay last.
5. Silence Android framework ExplicitGcViolation debug warnings without silencing other StrictMode diagnostics.
6. Add dedicated tests for conservative publication-year guessing.

## Priority 1 — Navigation and design coherence
7. Make the media types explicit on the home screen: Manga/Comics, Books/Novels, Anime/Video, Audio, News.
8. Explain or consolidate Explore vs Discover: Explore currently browses source websites, Discover is AniList-driven title discovery. Proposed: **Browse** (source/catalog browsing) and **Discover** (personalized recommendations across media), cross-linked.
9. Present clear media selection on every global-search entry point.
10. Retain the one global search instead of duplicating searches per media type.
11. Use one shared card/toolbar/nav system for More, Settings, and all specialized screens.
12. Replace the current special-purpose More screen's card list with the same design language and spacing as Settings.
13. Redesign News & Followed Tags: readable feed, topic chips, follow/unfollow, saved stories, newest-first and source filter.
14. Redesign Books Catalog: covers, author/series/year/language, providers, formats and download/borrow/open buttons.
15. Redesign Unified Library: Manga, Books, Videos, Audio tabs and All; unified folders/collections; progress and continue-reading/watch.
16. Show source health (working, limited, unreachable) and the last successful scan without noisy alerts.
17. Use thoughtful empty states, per-source error explanations, search-state restoration and loading skeletons.
18. Consistent typography, controls, dark mode, edge-to-edge behavior and accessibility descriptions.
19. Preserve the unified list and private-category visibility settings.

## Priority 2 — Image translation comparable to Lens
20. Keep ML Kit OCR for Japanese/Chinese/Korean/Latin, translate locally, then overlay English directly onto original page.
21. Segment speech bubbles/panels rather than covering arbitrary rectangular image regions.
22. Replace flat-color patches with background-aware filling/inpainting when feasible.
23. Fit text within detected bubbles (vertical/horizontal text, fonts, line breaks, alignment).
24. One-tap translated/original toggle; optional side-by-side view.
25. Per-series preferred source language and target language, with English override.
26. Show OCR confidence and allow tap-to-correct wrongly recognized text.
27. Cache translations per page and target language; offer offline model download management and cache limits.
28. Never send private pages to a server unless a separately enabled cloud provider is explicitly authorized.

## Priority 3 — Search and richer source discovery
29. Improve reliable publication metadata: year from source APIs/structured fields, then mark estimated years distinctly.
30. Offer oldest/newest globally in addition to within-source sorting once normalized metadata can be stored.
31. Support date ranges (e.g. 1990–2000), full years, unknown dates, and optional date confidence.
32. Offer genre/demographic tags, language, reading direction, format and progress filters across media types.
33. Install source presets: Manga, Novels, Audiobooks, Video, and Multi-media.
34. Display new/updating/broken extension status, permissions, source language and attribution.
35. Keep Mangayomi JavaScript extensions first-class; do not claim a Dart extension works without a compatible runtime.
36. Per-source log and open-in-browser fallback when a site changes or requires legitimate sign-in.
37. Cross-source duplicate-title merging and preferred-source selection.

### Candidate BOOK/novel catalogs (not installed until verified)
38. Standard Ebooks — high-quality public-domain EPUB collections/catalog feed.
39. Wikisource — multilingual public-domain and freely licensed works.
40. Faded Page / Distributed Proofreaders Canada — public-domain ebooks; check region/rights.
41. Project Gutenberg Australia — regional public-domain library; respect copyright territories.
42. Global Grey Ebooks — historical ebooks, where metadata and formats allow.
43. Entertainment-focused public-domain fiction catalogs - fantasy, romance, horror and science fiction with legal EPUB/PDF links.
44. Fanfiction discovery - fandom, ship, language, rating and story-update filters across installed sources.
45. StoryWeaver — multilingual Creative Commons stories, subject to its API/usage rules.
46. Google Books — metadata and legally offered previews only, not bypassing access limits.
47. Baen Free Library — legitimately free titles; check format and access.
48. FictionPress / FanFiction.net — public stories where site rules and scraping restrictions allow.
49. Library OPDS 1.2/2.0 support, including user-supplied catalogs, paging, searching and authentication.

### Candidate VIDEO sources/features (not installed until verified)
50. Vimeo public videos, channels and collections where embedding/playback is allowed.
51. Dailymotion public videos and channels.
52. Internet Archive Moving Image collection with direct public media files.
53. Bilibili public videos, where regional availability and site restrictions permit.
54. Odysee public videos and channels; verify licensing and streams.
55. Twitch publicly accessible clips/VODs, with appropriate API credentials.
56. Independent films, anime trailers, fan videos and entertainment video collections with permitted playback.
57. Jellyfin home-server libraries through the supported server API (user's own server).
58. Plex home-server libraries through authenticated APIs, subject to provider constraints.
59. Direct HLS/DASH files and permitted M3U playlists, with visible stream/codec diagnostics.
60. Video RSS and podcast feeds, playlists, chapter markers and subtitle tracking.
61. Do not attempt to bypass DRM/paywalls; link out to official paid services when in-app playback is unavailable.

## Priority 4 — Tablets and laptop-sized windows
62. Adaptive navigation rail on tablets, side drawer on desktop widths, bottom bar on phones.
63. Two-pane library/search/details interface at widths >= 600 dp and a three-column desktop layout where practical.
64. Responsive book/video grids, meaningful max text width and expanded reader controls for 13-inch tablets.
65. Proper portrait large-screen resources, not just landscape 600dp alternatives.
66. Resizable/freeform multiwindow, foldable posture and rotation-state testing.
67. Keyboard shortcuts, mouse scroll and hover, right click/context menu and D-pad navigation.
68. Drag/drop local EPUB, PDF, CBZ, images and supported media.
69. Surface accessibility scaling and touch-target checks.

## Priority 5 — Windows
70. Short-term: Android emulator for compatibility/testing only; do not mistake it for native Windows support.
71. Investigate a Kotlin Compose Multiplatform Windows client with a shared Kotlin core/parser/runtime and a dedicated desktop UI.
72. First desktop milestone: local EPUB/PDF/CBZ library, login-free search, reading progress and storage.
73. Second: source repositories, network reading, extensions, video playback and optional sync.
74. Explore a browser/PWA interface only for parts where browser CORS, cookies and storage rules allow.
75. Validate Windows file dialogs, background updates, multiwindow, hardware decoding and portable packaging.

## Priority 6 — Theme and branding
76. Keep existing light/dark and theme chooser; add a user-picked accent color.
77. Add multicolor mode with a restrained palette mapped to Books/Manga/Video/News, not random colors per widget.
78. Offer palette presets: ocean-teal, vaporwave teal-purple, pastel, monochrome and system dynamic color where available.
79. Preview color palette live and preserve contrast/readability in dark mode.
80. Replace the inherited bear identity with an original mascot, after choosing among a teal fox with bookmark-shaped tail, a glow-moth or a star-tailed tanuki.
81. Use the mascot sparingly (app icon, empty state, first-run tips), not as intrusive decoration.

## Priority 7 — Upstream and release quality
82. Daily meaningful-change watch for Kotatsu-Redo, kotatsu-parsers-redo, Mangayomi and relevant source/runtime projects.
83. Document upstream revision, license, modifications and tests for each ported component.
84. Audit updates before merging; do not silently overwrite local additions with copied upstream files.
85. Android CI: unit tests, assembler/R8 and actual emulator cold-start + basic navigation smoke checks.
86. Tablet emulation screenshots and layout checks; manual device QA only after CI.
87. Clearly distinguish debug preview, release signing and verified device-tested version.
88. Preserve user data, backups, settings and reading progress across updates.
