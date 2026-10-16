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

## Next implementation batch (October 16, 2026)

- #14: Added a user-triggered offline EPUB import for compatible free Standard Ebooks and Project Gutenberg releases; publisher link and redirect host validation, 32 MiB transfer limit, ZIP/mimetype checks, 384 MiB expanded-size guard, temporary-file cleanup, stable local shelf identity, and direct launch into the in-app EPUB reader. Protected/borrowed items remain official-link-only.
- #32: Format filters no longer leave unrelated parser-backed manga/video rows visible; selecting a language does not falsely include unknown-language books, and genre offers real provider-tag suggestions. Reading-direction choices are limited to titles in the visible search data.
- #63/#66: Tablet search preview restores the selected manga/official catalog item across rotation after results load. Unified Library RecyclerView now has a persistent Android resource ID for scroll-state restoration, with a recoverable library I/O error state.
- #48: Fiction browsing remains restricted to the provider originally opened and offers explicit feedback for site HTTP 403/429/503, without pretending anti-bot access is a native parser.
- GitHub development workflow: `.github/workflows/code-compile.yml` compiles app and unit-test **source code only**, with no emulator, signing, runtime feature tests, or APK publishing. Full verification deliberately remains the last phase.

## Follow-up implementation batch: unified public catalogs and offline actions (October 16)

- A **single registry** in `PublicCatalogProvider.kt` now drives all 11 existing public Books providers and the global "All"/Books search. The global search no longer quietly excludes Gutenberg, Open Library, Wikisource, Google Books, Faded Page, Gutenberg Australia, Global Grey and Baen Free Library.
- Public catalog results are appended **progressively** with bounded parallelism and 15-second source timeouts. They retain stable provider order, preserve results from successful sources and show a separate, **non-clickable** notice naming providers that did not respond. Official FictionPress/FanFiction.net website search links stay explicitly marked as such, not indexed native results.
- Genre and demographic filters can now use **actual provider-reported book subjects**, where available, from Gutenberg, Open Library and Google Books. The catalog never guesses subjects based on an unrelated title.
- Global-search results now carry publisher-reported free EPUB links. Eligible Standard Ebooks and Project Gutenberg results offer the same "Save EPUB and read offline" flow as Books, including from the tablet preview pane; protected and unapproved sources still open official listings.
- `PublicCatalogProviderTest.kt` adds source-coverage regression cases. New adapter state distinguishes source failures from empty titles; no feature/device acceptance has been performed.
- **Do not mark any of the 14 PARTIAL items DONE yet.** GitHub Actions should run only the compile-only workflow during this code-development phase. Provider access, live downloads, device layout and user acceptance remain final-stage work.
