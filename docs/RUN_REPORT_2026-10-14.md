# Finishing pass 2026-10-14 - run report

**Build check (green, includes unit tests, debug APK, R8 release and emulator cold start on phone + tablets):**
https://github.com/coolchan03/Monogatari/actions/runs/38514001195 - debug APK artifact `debug-apk`, cold-start logs `cold-start-logs`
(the final docs-only commit has its own run; its link is in the pull request).

Tracker after this pass: **43 DONE, 25 PARTIAL, 20 PENDING** (was 27 / 36 / 25). "DONE" still means code exists; nothing here has been tried on a device.

## Finished (item numbers)

| # | What |
|---|---|
| 24 | Translated page viewer with one-tap Show original / Show translation |
| 25 | Reader options > Translation languages for this series (source + target, saved per series) |
| 26 | OCR confidence per bubble; tap a bubble to see the recognised text and confidence and type a correction (redrawn and saved) |
| 21, 23 | Improved, still PARTIAL: neighbouring OCR regions merge into one bubble (right-to-left for vertical CJK); balanced line breaks; text that does not fit grows the bubble within the page |
| 8 | Discover explains itself; every section ends with "Browse source websites" (lands on Explore); Explore menu has "Discover recommendations" |
| 11, 12 | More hub now groups destinations under Settings-style section headings (still PARTIAL: Settings and More do not yet share one component) |
| 15 | Library "Continue" strip (recent titles, follows the tab). Still PARTIAL: unified folders/collections |
| 62 | Windows >= 1000dp start with the expanded labelled side drawer |
| 63 | Unified library has a details pane (cover, description, Open) at >= 840dp. Still PARTIAL: search and details screens are single-pane |
| 30 | "All sources together" oldest/newest ordering |
| 37 | Partly: same title from several sources shown once in that global ordering |
| 39 | Wikisource source (MediaWiki API) |
| 44 | AO3 search tokens: `fandom:"X" ship:"A/B" rating:teen lang:es updated:week` (never wider than your content level) |
| 54 | Odysee source (public free videos, mature tags excluded) |
| 57 | Jellyfin: More > Jellyfin server (address + API key), then the Jellyfin source; key sent as a header, never in URLs |
| 59 | "Direct links" source: paste .m3u8 / .mpd / file / .m3u link into video search; quality names show resolution, codecs, bitrate |
| 76, 78, 79 | Settings > Appearance > Accent colour: presets (theme default, Ocean teal, Vaporwave, Pastel, Monochrome) + any hex, live preview, low-contrast warning (Android 12+ only) |
| 83, 84 | `docs/PORTED_COMPONENTS.md`; audit gate and local-only file list in `docs/UPSTREAM_REVISION_LEDGER.md` |
| 71-75 | Plan only: `docs/WINDOWS_PLAN.md` |

Each user-visible feature has a Test Center step (translate_toggle, translate_languages, translate_correct, translate_bubbles, explore_vs_discover, library_continue, desktop_drawer, library_two_pane, wikisource, odysee, jellyfin, direct_streams, ao3_filters, accent_color, global_year_sort, font_scale_touch, resize_foldable). New pure logic has unit tests (sidecar/bubble grouping, nav policy, accent maths, year sorting, AO3 filters, stream links, Wikisource/Odysee/Jellyfin parsers).

## Not finished, and why

- **#40 Faded Page, #41 Gutenberg Australia, #42 Global Grey, #45 StoryWeaver:** these have no documented public API I could rely on, and I had no network access to check their pages, so any scraper would have been guesswork. Skipped rather than shipping sources that probably break.
- **Wikisource, Odysee and Jellyfin were written from API documentation without hitting the live services.** Parsers are unit-tested against sample JSON only. Expect the first device test to find field-name surprises, especially Odysee (`claim_search` proxy host and stream URLs).
- **#29 estimated-year labels, #31 date ranges/unknown/confidence, #32 cross-media filters:** the year range filter already existed; no new UI for confidence or cross-media filters.
- **#16, #17, #18, #36, #13, #14:** not started beyond one accessibility description; no new skeleton/empty-state work.
- **#22 inpainting, #27 model download management:** not touched.
- **#66, #69:** only Test Center steps; no code changes (needs a foldable/resizable emulator).
- **#80, #81 mascot:** waiting for your choice. **#58 Plex, #55 Twitch:** need your credentials. **#71-75:** plan only.
- **Accent colour on Android 11 and older:** not supported (Material's colour override needs Android 12+); the dialog says so.
- Zoom position is not kept when toggling original/translated (the image library API I could verify does not expose it).

## Test Center steps to try first

1. Translate a raw page, tap Show original, then tap a bubble and correct it.
2. Reader options > Translation languages for this series.
3. Settings > Appearance > Accent colour, in light and dark.
4. Video search: paste an https .m3u8 link; Books search: `fandom:"Given" updated:month`.
5. More > Jellyfin server (if you have one); Wikisource and Odysee searches.
6. Unified library on a tablet/landscape: details pane and Continue strip.

## Decisions I need from you

1. Mascot: teal fox, glow-moth or star-tailed tanuki? (#80/#81)
2. Native Windows app (multi-month) or keep using the Android emulator/Chromebook? (`docs/WINDOWS_PLAN.md`)
3. Should the new built-in sources (Wikisource, Odysee, Jellyfin, Direct links) be enabled for everyone by default? Right now they are, like all built-in sources; Jellyfin and Direct links return nothing until used.
4. For the sources I skipped (#40-42, #45): if you know an official API or feed for any of them, send the URL and I will add it.
5. Accent colour: keep it Android 12+ only, or invest in a fallback for older phones?

## Process notes

- AGENTS.md says to request "Tunnel to Surface" first and never use the GitHub connector to write. No Tunnel tool exists in this unattended session, so I edited and pushed with plain git and used the GitHub tools only to read CI and (as the task required) to open the pull request.
- One build failed along the way and was fixed: an unsupported image-viewer call (`ssiv.state`), caught by the first CI run.

## Follow-up after your answers (same day)

- **Windows app dropped** (#71-75): you will use an emulator. Marked as dropped in the tracker.
- **Built-in sources on by default:** already the case (all built-in sources are enabled and pinned); nothing to change.
- **Accent colour fallback:** Android 11 and older now show any accent as black (light mode) / white (dark mode).
- **Also finished:** #22 smooth background fill for shaded bubbles, #27 offline translation language management, #43 Gutenberg genre search.
- **Mascot:** a cast of small creatures instead of one, proposal in `docs/MASCOT_IDEAS.md`; waiting for your pick.
- **"GitHub project that gives API access to anything":** I could not identify it from the description. Tools like RSSHub or RSS-Bridge turn websites into feeds; the app's Podcasts & video RSS and News screens already read feeds, so such a feed URL works today. Send the project's name or link and I will check whether a source can be built on it.
- **Still open:** #40/#41/#42/#45 (need an official feed or the project above), #29/#31/#32, #16-#18, #36 (list screens already have "open in browser"), #66/#69, #13/#14, #47/#48/#50/#53/#56, #55/#58 (credentials).
