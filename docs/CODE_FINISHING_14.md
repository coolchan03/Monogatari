# Monogatari: final fourteen Android roadmap items

**Work branch:** `finish-83-20261015` (based on `devel` at `eaae55c`).
**Policy:** Complete and push app changes first. GitHub-hosted compilation checks may flag programming errors. Device/provider feature verification and the first official v1.0.0 release are **last**, not prerequisites for writing code. This sheet is **not** release sign-off.

| ID | Code implementation on this finishing branch | Deferred final acceptance |
| --- | --- | --- |
| 11 | Unified Material3 chrome/Up affordance on QA, More and custom screens; consistent catalog/reader navigation | Inspect every specialized Activity's back/up and theme |
| 14 | Books metadata, editions, series, availability disclosures, official source options, covers | Sample providers for metadata/lending truth |
| 17 | Persistent search filters, query and library selection; Books skeleton, News rotation/scroll restoration and feed loading/retry | Rotate/search/interrupt each screen |
| 18 | Theme typography on More/news/media screens, responsive controls, translated font options | 200% font and all light/dark screen inspection |
| 21 | Connected paper flood mask, path-clipped OCR removal and panel-boundary guard | Review real manga speech bubbles on devices |
| 23 | Dynamic text fit, RTL/CJK layout and saved sans/serif/comic translation font | Real pages at different scales and scripts |
| 32 | Genre/demographic tags, source language, formats, actual local history and saved reader-direction filters | Compare mixed manga/book/video result sets |
| 37 | Conservative title+author/alternate-title grouping, source choice persisted per title | Search different creators and multiple sources |
| 45 | StoryWeaver live authorized public catalog metadata, covers, publisher/writer/illustrator credits and in-app official reader | Site display and rights/attribution spot checks |
| 48 | FictionPress/FanFiction.net clearly labeled official-site searches, contained in-app browser, reload and external browser fallback; no unsupported HTML parsing or CAPTCHA bypass | Log-in/CAPTCHA behavior belongs to the provider |
| 58 | Plex header-authenticated direct-play and optional token-safe server HLS fallback, codec/access errors | Test owner Plex server and media codecs |
| 63 | Search preview panes at 600dp/840dp; multi-column wide library and details | Narrow/wide tablet split screen |
| 66 | Saved search/genre/history/direction, library pane/collection/query, News filter/position and translated WebView state | Rotate and resize Razr foldable, tablet freeform |
| 69 | Catalog unlimited text lines, minimum touch targets, QA header/navigation labels, accessibility-focused static checks | TalkBack/contrast/font-scale review |

Important constraints:
- The 14 tracker rows remain **PARTIAL** until the final acceptance stage. Code presence is not evidence that a provider or screen works on the owner's device.
- FictionPress/FanFiction.net do not have an available documented native discovery/chapter API in this integration. This is an explicit official-web experience, not a fictitious extension.
- Plex credentials must never appear in public URLs, logs, issue descriptions, or tests. Only use mocked IDs in source-controlled tests.
- Do not merge or publish a signed v1.0.0 based solely on an unverified build. Build/test evidence and owner acceptance belong to the last stage.
- Keep the original dirty P: checkout untouched; all development is in the dedicated clean-origin worktree.
